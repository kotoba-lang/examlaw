(ns kotoba.examlaw-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.examlaw :as law]))

;; ---------------------------------------------------------------------------
;; The invariant. Everything else in this file is detail.
;; ---------------------------------------------------------------------------

(deftest absence-is-never-authority
  (testing "a jurisdiction nobody has read does not permit anything"
    (let [r (law/examination [:atlantis] :exam/field-visit
                             {:advance-notice-given? true
                              :identification-carried? true
                              :criminal-investigation? false})]
      (is (= :none (:examlaw/coverage r)))
      (is (= [[:atlantis]] (:examlaw/unchecked r)))
      (is (false? (law/authorized? r)))
      (is (= :no-catalog (law/disposition r)))
      (testing "and there is no key a caller could mistake for a permission"
        (is (not (contains? r :examlaw/authority)))
        (is (not (contains? r :examlaw/permitted?))))))

  (testing "a catalogued jurisdiction silent on THIS action is equally not a pass"
    (let [r (law/examination [:eu] :exam/question {})]
      (is (= :none (:examlaw/coverage r)))
      (is (false? (law/authorized? r)))
      (is (= :action-not-catalogued-here (:examlaw/reason r)))))

  (testing "authorized? is not (empty? unmet)"
    (let [r (law/examination [:jp] :exam/question {})]
      (is (empty? (:examlaw/unmet r)) "nothing is positively violated")
      (is (false? (law/authorized? r)) "and it is still not authorized"))))

;; ---------------------------------------------------------------------------
;; Japan
;; ---------------------------------------------------------------------------

(def jp-clean-field-visit
  {:field-visit? true
   :criminal-investigation? false
   :identification-carried? true
   :advance-notice-given? true
   :notice-items #{:notice/start-datetime :notice/place :notice/purpose
                   :notice/tax-items :notice/periods :notice/records}})

(deftest jp-field-visit
  (testing "a fully documented field visit still defers — 必要があるとき is the officer's finding"
    (let [r (law/examination [:jp] :exam/field-visit jp-clean-field-visit)]
      (is (= :checked (:examlaw/coverage r)))
      (is (empty? (:examlaw/unmet r)))
      (is (contains? (set (:examlaw/official-determination r)) :jp/necessity))
      (is (= :requires-official-determination (law/disposition r)))
      (is (false? (law/authorized? r)))))

  (testing "a notice missing one of the six statutory items is blocked, not deferred"
    (let [r (law/examination [:jp] :exam/field-visit
                             (update jp-clean-field-visit :notice-items disj :notice/periods))]
      (is (= [:jp/notice-contents] (:examlaw/unmet r)))
      (is (= :blocked (law/disposition r)))))

  (testing "no notice at all is deferred to 第七十四条の十, not blocked and not permitted"
    (let [r (law/examination [:jp] :exam/field-visit
                             (assoc jp-clean-field-visit :advance-notice-given? false))]
      (is (empty? (:examlaw/unmet r))
          "74-10 lets 税務署長等 dispense with notice on their own finding")
      (is (contains? (set (:examlaw/deferred r)) :jp/advance-notice))
      (is (contains? (set (:examlaw/official-determination r)) :jp/notice-exception))
      (is (= :requires-official-determination (law/disposition r)))
      (is (false? (law/authorized? r)))))

  (testing "74-9(4): questioning on a matter outside the notified scope needs no notice"
    (let [r (law/examination [:jp] :exam/question
                             (assoc jp-clean-field-visit
                                    :advance-notice-given? false
                                    :outside-notified-scope? true))
          ids (set (map :req/id (:examlaw/requirements r)))]
      (is (contains? ids :jp/scope-widening-allowed))
      (is (not (contains? (set (:examlaw/unmet r)) :jp/advance-notice)))))

  (testing "74-8: the power is not a criminal-investigation power"
    (let [r (law/examination [:jp] :exam/question
                             (assoc jp-clean-field-visit :criminal-investigation? true))]
      (is (= [:jp/not-criminal-investigation] (:examlaw/unmet r)))
      (is (= :blocked (law/disposition r)))))

  (testing "identification is required and its absence is unverified, not satisfied"
    (let [r (law/examination [:jp] :exam/question
                             (dissoc jp-clean-field-visit :identification-carried?))]
      (is (contains? (set (:examlaw/unverified r)) :jp/identification))
      (is (false? (law/authorized? r)))))

  (testing "74-11: closing a field visit with no correction requires a WRITTEN notice"
    (let [r (law/examination [:jp] :exam/close-examination
                             {:field-visit? true :correction-intended? false
                              :written-no-correction-notice? false})]
      (is (= [:jp/no-correction-notice] (:examlaw/unmet r)))))

  (testing "74-11(3): urging an amended return requires handing over the appeal-rights document"
    (let [r (law/examination [:jp] :exam/close-examination
                             {:field-visit? true :correction-intended? true
                              :result-explained? true
                              :amended-return-urged? true
                              :appeal-rights-document-given? false})]
      (is (= [:jp/amended-return-writing] (:examlaw/unmet r)))))

  (testing "74-7: retention reaches only items produced in THIS examination"
    (let [r (law/examination [:jp] :exam/retain-items
                             {:produced-in-this-examination? false})]
      (is (= [:jp/produced-in-this-examination] (:examlaw/unmet r))))))

;; ---------------------------------------------------------------------------
;; United States
;; ---------------------------------------------------------------------------

(deftest us-examination
  (testing "7602(b): an offense inquiry is an ALLOWED purpose — the opposite of JP 74-8"
    (let [facts {:criminal-investigation? true :in-person-interview? false}
          jp (law/examination [:jp] :exam/question facts)
          us (law/examination [:us] :exam/question facts)]
      (is (= :blocked (law/disposition jp)))
      (is (not= :blocked (law/disposition us))
          "no US requirement is violated by inquiring into an offense")
      (is (false? (law/authorized? us)) "and it is still not a permission")))

  (testing "7602(d): but a summons is blocked once a DOJ referral is in effect"
    (let [r (law/examination [:us] :exam/question
                             {:summons? true :doj-referral-in-effect? true
                              :summons-notice-days 30})]
      (is (= [:us/no-doj-referral] (:examlaw/unmet r)))))

  (testing "7605(a): a summons must give at least 10 days"
    (is (= [:us/summons-10-days]
           (:examlaw/unmet (law/examination [:us] :exam/question
                                            {:summons? true :doj-referral-in-effect? false
                                             :summons-notice-days 9}))))
    (is (empty? (:examlaw/unmet (law/examination [:us] :exam/question
                                                 {:summons? true :doj-referral-in-effect? false
                                                  :summons-notice-days 10})))))

  (testing "7521(b)(2): a stated wish to consult suspends the interview"
    (let [r (law/examination [:us] :exam/question
                             {:in-person-interview? true :initial-interview? false
                              :consultation-requested? true :interview-suspended? false
                              :criminal-investigation? false})]
      (is (= [:us/suspend-on-consultation] (:examlaw/unmet r)))))

  (testing "7521(d): none of the 7521 safeguards apply to a criminal investigation"
    (let [r (law/examination [:us] :exam/question
                             {:in-person-interview? true :initial-interview? true
                              :consultation-requested? true :interview-suspended? false
                              :recording-requested? true :recording-allowed? false
                              :criminal-investigation? true})
          unmet (set (:examlaw/unmet r))]
      (is (not (contains? unmet :us/suspend-on-consultation)))
      (is (not (contains? unmet :us/explain-audit-process)))
      (is (not (contains? unmet :us/allow-recording)))))

  (testing "7605(b): a second inspection is never authorized on a silent record"
    (let [r (law/examination [:us] :exam/re-examine {})]
      (is (= [:us/one-inspection] (:examlaw/unverified r))
          "NOT :unmet — the record failing to record the taxpayer's request is
           not the same claim as the second inspection being unlawful, and this
           library must not make the stronger claim on the weaker evidence")
      (is (= :requires-official-determination (law/disposition r)))
      (is (false? (law/authorized? r))))
    (testing "and a record that positively denies both exceptions IS blocked"
      (let [r (law/examination [:us] :exam/re-examine
                               {:taxpayer-requested-additional? false
                                :written-additional-inspection-notice? false})]
        (is (= [:us/one-inspection] (:examlaw/unmet r)))
        (is (= :blocked (law/disposition r)))))
    (let [r (law/examination [:us] :exam/re-examine
                             {:taxpayer-requested-additional? false
                              :written-additional-inspection-notice? true})]
      (is (empty? (:examlaw/unmet r)))))

  (testing "and Japan's default is the opposite — 74-11(5) permits it on new information"
    (let [jp (law/examination [:jp] :exam/re-examine {:criminal-investigation? false})]
      (is (empty? (:examlaw/unmet jp)))
      (is (= :requires-official-determination (law/disposition jp)))))

  (testing "7602(c): third-party contact needs 45 days' notice"
    (let [r (law/examination [:us] :exam/contact-third-party
                             {:third-party-notice-given? true
                              :third-party-notice-days 30
                              :contact-within-noticed-period? true})]
      (is (= [:us/third-party-notice] (:examlaw/unmet r))))
    (let [r (law/examination [:us] :exam/contact-third-party
                             {:third-party-notice-given? true
                              :third-party-notice-days 45
                              :contact-within-noticed-period? true})]
      (is (empty? (:examlaw/unmet r)))))

  (testing "7602(c)(3)(B): no notice defers to the Secretary's good-cause finding"
    (let [r (law/examination [:us] :exam/contact-third-party
                             {:third-party-notice-given? false})]
      (is (contains? (set (:examlaw/deferred r)) :us/third-party-notice))
      (is (= :requires-official-determination (law/disposition r)))))

  (testing "there is no general advance-notice requirement, and none was invented"
    (let [ids (into #{} (map :req/id)
                    (:examlaw/requirements (law/examination [:us] :exam/field-visit {})))]
      (is (not (contains? ids :us/advance-notice)))
      (is (= :read (get-in law/jurisdictions [[:us] :facets :exam/advance-notice :facet/status]))
          "read, and found absent — not silent"))))

;; ---------------------------------------------------------------------------
;; European Union — the interesting entry is the one with no power in it
;; ---------------------------------------------------------------------------

(deftest eu-has-no-examination-power
  (testing "every examination action is uncatalogued for [:eu]"
    (doseq [a [:exam/question :exam/inspect-books :exam/field-visit :exam/re-examine
               :exam/retain-items :exam/close-examination]]
      (is (= :none (:examlaw/coverage (law/examination [:eu] a {})))
          (str a " must not be answerable at EU level"))))

  (testing "cooperation IS catalogued"
    (let [r (law/examination [:eu] :exam/joint-audit
                             {:requested-authority-agreed? true
                              :host-arrangements-followed? true})]
      (is (= :checked (:examlaw/coverage r)))
      (is (empty? (:examlaw/unmet r)))
      (is (contains? (set (:examlaw/two-jurisdiction r)) :eu/home-power-ceiling)
          "12a(2)'s home-state ceiling cannot be evaluated from one jurisdiction")
      (is (false? (law/authorized? r))))))

(deftest joint-audit-needs-both-jurisdictions
  (testing "an unread home state makes the answer unknown, not the host's answer"
    (let [r (law/joint-audit [:jp] [:atlantis] :exam/inspect-books jp-clean-field-visit)]
      (is (= :none (:examlaw/coverage r)))
      (is (= [[:atlantis]] (:examlaw/unchecked r)))
      (is (= :cross-border-needs-both-jurisdictions (:examlaw/reason r)))))

  (testing "blocked on either side blocks the joint audit"
    (let [r (law/joint-audit [:jp] [:us] :exam/question
                             (assoc jp-clean-field-visit
                                    :criminal-investigation? true
                                    :requested-authority-agreed? true
                                    :host-arrangements-followed? true))]
      (is (= :blocked (:examlaw/disposition r))
          "JP 74-8 blocks the host side even though US 7602(b) permits it"))))

;; ---------------------------------------------------------------------------
;; Three-valued clause evaluation
;; ---------------------------------------------------------------------------

(deftest unknown-never-collapses
  (is (= :unknown (law/eval-clause [:fact-true :x] {})))
  (is (= :yes (law/eval-clause [:fact-true :x] {:x true})))
  (is (= :no (law/eval-clause [:fact-true :x] {:x false})))
  (is (= :unknown (law/eval-clause [:fact-false :x] {})))
  (testing "conjunction with an unknown is unknown, not false"
    (is (= :unknown (law/eval-clause [:all [:fact-true :a] [:fact-true :b]] {:a true}))))
  (testing "but a definite no still wins"
    (is (= :no (law/eval-clause [:all [:fact-true :a] [:fact-true :b]] {:a false}))))
  (testing "disjunction with a definite yes is yes even alongside unknown"
    (is (= :yes (law/eval-clause [:any [:fact-true :a] [:fact-true :b]] {:a true}))))
  (is (= :unknown (law/eval-clause [:at-least-days :d 45] {})))
  (is (= :unknown (law/eval-clause [:at-least-days :d 45] {:d "soon"})))
  (is (= :unknown (law/eval-clause [:has-all :k #{:a}] {}))))

;; ---------------------------------------------------------------------------
;; Coverage arithmetic
;; ---------------------------------------------------------------------------

(deftest depth-buckets-partition
  (doseq [j (keys law/jurisdictions)]
    (let [d (law/depth j)]
      (is (= (:of d) (+ (:read d) (:partly-read d) (:out-of-scope d) (:silent d)))
          (str j " buckets must partition the facet universe, not double-count")))))

(deftest world-coverage-requires-a-denominator
  (is (thrown? #?(:clj Exception :cljs js/Error) (law/world-coverage nil)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (law/world-coverage [])))
  (testing "and the facet total is lower than the jurisdiction fraction"
    (let [w (law/world-coverage #{[:jp] [:us] [:eu] [:de] [:fr] [:sg] [:br]})
          [jr jd] (:examlaw/jurisdiction-fraction w)
          [fr fd] (:examlaw/facet-total w)]
      (is (= 3 jr)) (is (= 7 jd))
      (is (< (/ fr fd) (/ jr jd))
          "reporting only 3/7 would flatter a catalog that has read 3 of 98 facets"))))

(deftest silent-is-distinguishable-from-out-of-scope
  (testing "US closing procedure is SILENT — nobody has looked"
    (is (= :silent (get-in law/jurisdictions [[:us] :facets :exam/closing-procedure :facet/status]))))
  (testing "US notice contents is OUT-OF-SCOPE — looked, and there is nothing to read"
    (is (= :out-of-scope (get-in law/jurisdictions [[:us] :facets :exam/notice-contents :facet/status])))
    (is (some? (:reason (law/out-of-scope-reason [:us] :exam/notice-contents)))
        "an out-of-scope facet must carry its reason"))
  (testing "both still answer :no-catalog to a consumer that has never heard of either"
    (is (= :none (:examlaw/coverage (law/examination [:us] :exam/close-examination {}))))))

;; ---------------------------------------------------------------------------
;; Catalog integrity
;; ---------------------------------------------------------------------------

(deftest every-requirement-cites-a-real-source
  (doseq [[jpath entry] law/jurisdictions
          [action reqs] (:actions entry)
          :when (vector? reqs)
          r reqs]
    (is (contains? law/sources (:req/source r))
        (str jpath " " action " " (:req/id r) " cites an unknown source"))
    (is (contains? law/facet-universe (:req/facet r))
        (str (:req/id r) " names a facet outside the universe"))))

(deftest every-catalogued-action-is-a-known-action
  (doseq [[jpath entry] law/jurisdictions
          action (keys (:actions entry))]
    (is (contains? law/actions action)
        (str jpath " catalogues an action not in the action universe: " action))))

(deftest quotes-are-verbatim-or-marked
  (doseq [[id s] law/sources]
    (is (string? (:source/quote s)))
    (is (pos? (count (:source/quote s))))
    (is (= (:source/elided? s) (boolean (re-find #"…" (:source/quote s))))
        (str id ": :source/elided? must match whether the quote actually contains an ellipsis"))
    (is (string? (:source/fetched s)))
    (is (string? (:source/url s)))))

(deftest the-us-quote-says-forty-five-days
  (is (re-find #"not later than 45 days before"
               (:source/quote (law/source :us-irc-7602-c)))))

(deftest jp-notice-item-count-is-not-folklore
  (testing "the Act lists seven; six are substantive; eleven is a different instrument"
    (is (= 7 (:statutory-count law/jp-notice-items)))
    (is (= 6 (count (:statutory law/jp-notice-items))))
    (is (= 11 (:commonly-quoted-count law/jp-notice-items)))
    (is (re-find #"NOT READ" (:commonly-quoted-source law/jp-notice-items)))))

(deftest recorded-absences-name-their-near-misses
  (doseq [a (:absences law/catalog-verification)]
    (is (string? (:absence/searched a))
        (str (:absence/id a) " must record what was searched"))
    (doseq [n (:absence/near-misses a)]
      (is (contains? law/sources n)
          (str (:absence/id a) " names a near-miss that is not in sources")))))

;; ---------------------------------------------------------------------------
;; The safety property, stated as a test rather than as a README paragraph
;; ---------------------------------------------------------------------------

(def maximally-favourable
  "Every fact this library knows how to read, set to the value most favourable
  to the authority. If `authorized?` can be coaxed to true for an examination
  power, this is the record that would do it."
  {:field-visit? true :criminal-investigation? false :identification-carried? true
   :advance-notice-given? true :outside-notified-scope? false
   :notice-items #{:notice/start-datetime :notice/place :notice/purpose
                   :notice/tax-items :notice/periods :notice/records}
   :produced-in-this-examination? true
   :summons? false :doj-referral-in-effect? false :summons-notice-days 90
   :in-person-interview? true :initial-interview? true :process-explained? true
   :consultation-requested? false :interview-suspended? true
   :recording-requested? false :recording-allowed? true
   :taxpayer-requested-additional? true :written-additional-inspection-notice? true
   :third-party-notice-given? true :third-party-notice-days 90
   :contact-within-noticed-period? true :taxpayer-authorized-contact? true
   :pending-criminal-investigation? false
   :requested-authority-agreed? true :host-arrangements-followed? true})

(def examination-powers
  "The actions that ARE an exercise of examination authority. Closing an
  examination and the cooperation formalities are not on this list."
  #{:exam/question :exam/inspect-books :exam/demand-production :exam/field-visit
    :exam/re-examine :exam/contact-third-party :exam/retain-items})

(deftest never-authorizes-an-exercise-of-examination-power
  (testing "no record, however complete, makes this library say an examination may proceed"
    (doseq [[jpath entry] law/jurisdictions
            action (keys (:actions entry))
            :when (contains? examination-powers action)]
      (let [r (law/examination jpath action maximally-favourable)]
        (is (false? (law/authorized? r))
            (str jpath " " action " was authorized by a library that must never authorize"))
        (is (= :requires-official-determination (law/disposition r))
            (str jpath " " action " must defer to a named official, not resolve"))))))

(deftest authorized?-is-not-constantly-false
  (testing "closing an examination is administrative, so it CAN come back clean —
            otherwise the property above would be vacuous"
    (let [r (law/examination [:jp] :exam/close-examination
                             {:field-visit? true :correction-intended? true
                              :result-explained? true :amended-return-urged? true
                              :appeal-rights-document-given? true})]
      (is (true? (law/authorized? r)))
      (is (= :no-catalogued-requirement-unmet (law/disposition r))))))

(deftest out-of-scope-is-bidirectional
  (testing "every facet marked out-of-scope resolves to a reason"
    (doseq [[jpath entry] law/jurisdictions
            [facet {st :facet/status}] (:facets entry)
            :when (= :out-of-scope st)]
      (is (some? (:reason (law/out-of-scope-reason jpath facet)))
          (str jpath " " facet " is out-of-scope with no reason — that is a
               deliberate exclusion nobody wrote down, which is indistinguishable
               from a facet nobody thought about"))))

  (testing "and every recorded reason belongs to a facet actually marked out-of-scope"
    (doseq [[jpath entry] law/jurisdictions
            facet (keys (:out-of-scope entry))]
      (is (= :out-of-scope (get-in entry [:facets facet :facet/status]))
          (str jpath " " facet " carries an out-of-scope reason but is not
               marked out-of-scope — the reason is unreachable and the reader
               of `depth` never sees it"))))

  (testing "a facet nobody read is silent, not out-of-scope, even when the gap is annotated"
    (is (= :silent (get-in law/jurisdictions [[:jp] :facets :exam/cross-border :facet/status])))
    (is (nil? (law/out-of-scope-reason [:jp] :exam/cross-border)))
    (is (some? (get-in law/jurisdictions [[:jp] :facets :exam/cross-border :facet/note])))))
