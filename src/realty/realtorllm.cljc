(ns realty.realtorllm
  "Realtor-LLM client -- the *contained intelligence node*.

  It normalizes listing intake, drafts a per-jurisdiction
  disclosure/title-document checklist, screens parties against a
  KYC/sanctions signal, and drafts the closing-submission action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a *proposal*
  (with a rationale + the fields it cited), never a committed record or a
  real title-transfer recording / escrow disbursement. Every output is
  censored downstream by `realty.governor` before anything touches the
  SSoT, and `:closing/submit` proposals NEVER auto-commit at any phase --
  see README `Actuation`.

  Like `formation.registrarllm` / `talent.hrllm` / `itonami.opsllm`, this
  is a deterministic mock so the actor graph runs offline and the governor
  contract is exercised end-to-end. In production this calls a real LLM
  (kotoba-llm or equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation if it touches a real closing
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [realty.facts :as facts]
            [realty.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it does
  not invent parties, price or jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "物件レコード更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :listing/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction disclosure/title-document checklist draft. `:no-spec?`
  injects the failure mode we must defend against: proposing a checklist
  for a jurisdiction with NO official spec-basis in `realty.facts` -- the
  RealtorGovernor must reject this (never invent a jurisdiction's law)."
  [db {:keys [subject no-spec?]}]
  (let [l (store/listing db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction l))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "realty.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :disclosure/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-docs sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :disclosure/set
       :value      {:jurisdiction iso3
                    :checklist (:required-docs sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-kyc
  "KYC / sanctions screening draft. `:sanctions-hit?` on the party record
  injects the failure mode: the RealtorGovernor must HOLD, un-overridably,
  on any sanctions/PEP hit. Missing identification yields low confidence
  -> escalate rather than auto-clear."
  [db {:keys [subject]}]
  (let [p (store/party db subject)]
    (cond
      (nil? p)
      {:summary "対象partyが見つかりません" :rationale "no party record"
       :cites [] :effect :kyc/set :value {:party-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (:sanctions-hit? p)
      {:summary    (str (:name p) ": 制裁/PEPリストと一致")
       :rationale  "スクリーニングが一致を検出。人手確認とホールドが必須。"
       :cites      [:sanctions-list]
       :effect     :kyc/set
       :value      {:party-id subject :verdict :hit}
       :stake      nil
       :confidence 0.95}

      (nil? (:id-doc p))
      {:summary    (str (:name p) ": 本人確認書類が未提出")
       :rationale  "本人確認書類が無いため確信度を上げられない。"
       :cites      [:id-doc]
       :effect     :kyc/set
       :value      {:party-id subject :verdict :incomplete}
       :stake      nil
       :confidence 0.4}

      :else
      {:summary    (str (:name p) ": 制裁リスト一致なし、本人確認書類あり")
       :rationale  "本人確認書類確認 + 制裁リスト非一致。"
       :cites      [:id-doc :sanctions-list]
       :effect     :kyc/set
       :value      {:party-id subject :verdict :clear}
       :stake      nil
       :confidence 0.9})))

(defn- propose-closing
  "Draft the actual title-transfer-recording + escrow-disbursement action.
  ALWAYS `:stake :actuation` -- this is a REAL-WORLD act (a land registry
  records a transfer; escrow funds move), never a draft the actor may
  auto-run. See README `Actuation`: no phase ever adds this op to a
  phase's `:auto` set (`realty.phase`); the governor also always
  escalates on `:actuation`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [l (store/listing db subject)
        disclosure (store/disclosure-of db subject)
        docs-ok? (and disclosure (facts/required-docs-satisfied?
                                  (:jurisdiction l)
                                  (:checklist disclosure)))]
    {:summary    (str (:address l) " (" (:jurisdiction l)
                      ") の成約準備ができました" (when-not docs-ok? " (書類未充足)"))
     :rationale  (if disclosure
                   (str "spec-basis: " (:spec-basis disclosure))
                   "disclosure未実施")
     :cites      (if disclosure [(:spec-basis disclosure)] [])
     :effect     :closing/mark-recorded
     :value      {:listing-id subject}
     :stake      :actuation
     :confidence (if docs-ok? 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :listing/intake       (normalize-intake db request)
    :jurisdiction/assess  (assess-jurisdiction db request)
    :kyc/screen           (screen-kyc db request)
    :closing/submit       (propose-closing db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは不動産仲介エージェントの助言者です。与えられた事実のみに"
       "基づき、提案を1つだけEDNマップで返します。説明や前置きは一切書かず、"
       "EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:listing/upsert|:disclosure/set|:kyc/set|:closing/mark-recorded) "
       ":stake(:actuation か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess {:listing (store/listing st subject)}
    :kyc/screen          {:party (store/party st subject)}
    :closing/submit      {:listing (store/listing st subject)
                          :disclosure (store/disclosure-of st subject)}
    {:listing (store/listing st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure yields
  a safe low-confidence noop so the RealtorGovernor escalates/holds -- an
  LLM hiccup can never auto-close or auto-disburse."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :realtorllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
