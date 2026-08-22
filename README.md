# kotoba-examlaw

**What a tax authority must have done before it may examine — by jurisdiction.**
A [kotoba-lang](https://github.com/kotoba-lang) capability library that answers
one question: in this jurisdiction, does the record establish what the law
requires before this examination step may be taken?

> **Not legal advice, and not a tax administration system.** This is a
> mechanism plus a small, cited rule set. It is deliberately incomplete, and
> its most important behaviour is what it does about that.

Sibling of [`kotoba-lang/taxlaw`](https://github.com/kotoba-lang/taxlaw) and
[`kotoba-lang/worklaw`](https://github.com/kotoba-lang/worklaw), and the same
shape on purpose — with one inversion that is the reason it is a separate
library rather than a namespace inside `taxlaw`.

## The invariant, inverted: absence is never authority

`taxlaw` and `worklaw` are keyed on a **private party's obligation**, so their
conservative direction is to withhold a pass: *absence is never sufficiency*.
This library is keyed on a **state power**. The conservative direction
therefore flips.

```clojure
(require '[kotoba.examlaw :as law])

(law/examination [:atlantis] :exam/field-visit
                 {:advance-notice-given? true :identification-carried? true})
;; => {:examlaw/coverage :none
;;     :examlaw/unchecked [[:atlantis]]
;;     :examlaw/reason :jurisdiction-not-in-catalog
;;     :examlaw/requirements []}
;;    no :examlaw/authority key at all — nobody read this country's procedure code

(law/authorized? *1)   ;; => false
```

A caller cannot read *we have not read this country's procedure code* as *the
officer may proceed*. `authorized?` is deliberately **not**
`(empty? (:examlaw/unmet result))`; a caller who reaches for the convenient
boolean gets the conservative answer rather than the flattering one.

Replacing `authorized?` with `(empty? unmet)` turns **21 assertions** red,
including the two tests that exist only to state this property. Measured, not
estimated — see the last section.

## This library never authorizes an exercise of examination power

That is a test, not a paragraph:

```clojure
(deftest never-authorizes-an-exercise-of-examination-power ...)
```

It builds the record most favourable to the authority that this library knows
how to read — every notice given, every safeguard honoured, no criminal
referral, ninety days where forty-five are needed — and asserts that
`authorized?` is **false** for every examination power in every catalogued
jurisdiction, and that `disposition` is
`:requires-official-determination`.

It holds because every examination power in every catalogued jurisdiction
begins with a condition the statute commits to a named official's own finding
— 国税通則法第七十四条の二's *調査について必要があるとき*, 26 U.S.C.
§7602(a)'s *for the purpose of ascertaining the correctness of any return*.
Those are marked `:req/kind :official-determination` and are never
machine-satisfiable. The library reports **what a human must decide**; it does
not decide it.

A property this strong is worth nothing if it is vacuous, so a second test
asserts `authorized?` *can* be true — for `:exam/close-examination`, which is
administrative rather than an exercise of power.

**The property earned itself immediately.** On first run it failed for
`[:us] :exam/contact-third-party`, because that action's requirement list had
the §7602(c) notice arithmetic but not §7602(a)'s purpose or §7602(c)(1)'s
*"unless there is an intent at the time such notice is issued"*. With a
perfect notice record the library said the contact could go ahead. Two
requirements were missing from the catalog and the property found them.

## Three-valued, because two values cannot say "the record does not say"

`:met` / `:unmet` / `:unverified` are three different answers and collapsing
the third into either of the others is a bug in a specific direction.

```clojure
(law/eval-clause [:all [:fact-true :a] [:fact-true :b]] {:a true})
;; => :unknown        NOT :no — nobody wrote down whether b happened
```

This bit during development. 26 U.S.C. §7605(b) says only one inspection per
taxable year *"unless the taxpayer requests otherwise or unless the Secretary
… notifies the taxpayer in writing"*. The first version of the test asserted
that a silent record makes a second inspection `:blocked`. That is the
stronger claim, and the evidence does not support it: a record that does not
mention the taxpayer's request is not a record that the taxpayer did not make
one. The library reports `:unverified` and `authorized?` stays false either
way — but only `:unmet` asserts that the law was broken, and this library
should not assert that from silence. The test was wrong; the code was right.

A record that *positively denies* both exceptions is a different matter, and
that one is `:blocked`.

## Four dispositions, and the fourth is not called `:permitted`

| | |
|---|---|
| `:no-catalog` | nothing here was checked |
| `:blocked` | a catalogued requirement is positively unmet |
| `:requires-official-determination` | a statute commits something to an official's finding, or the record does not establish a requirement |
| `:no-catalogued-requirement-unmet` | every catalogued requirement the record speaks to is satisfied |

The fourth is a statement about **this catalog**, which has read three
jurisdictions on fourteen facets. It is not a statement about the law, and it
is not a decision an official has made. Naming it `:permitted` would invite
exactly the reading it must not have.

## Three jurisdictions, and the pairs that point in opposite directions

| | read from source |
|---|---|
| `[:jp]` | 国税通則法 第74条の2・74条の7・74条の8・74条の9・74条の10・74条の11・74条の13・128条 |
| `[:us]` | 26 U.S.C. §7602(a)(b)(c)(d), §7605(a)(b), §7521(a)(b)(d) |
| `[:eu]` | Directive 2011/16/EU Art 11(1), 12(1), 12a(1), 12a(2) |

Every quote was fetched from the official publisher on 2026-08-22 — e-Gov 法令
API for the Act (revision `337AC0000000066_20260624_508AC0000000046`), govinfo
for the 2023 edition of the Code, EUR-Lex for the 2024-01-01 consolidation —
and is a byte-exact span of what that endpoint returned. A test asserts that
`:source/elided?` matches whether the quote actually contains an ellipsis, so
a silently shortened quote fails the build. It caught one on first run.

**A checker that learned one of these jurisdictions and answered for the other
would be confidently wrong, twice:**

- **Criminal purpose.** 国税通則法第七十四条の八: the questioning-and-inspection
  power *「犯罪捜査のために認められたものと解してはならない」*. 26 U.S.C.
  §7602(b): the purposes *"include the purpose of inquiring into any offense
  connected with the administration or enforcement of the internal revenue
  laws."* Same fact — an officer inspecting books while suspecting an offense
  — **blocked in Japan, permitted in the United States.** (The US then blocks
  the *summons* once a Justice Department referral is in effect, §7602(d) —
  a different line in a different place.)
- **Re-examination.** §7605(b) makes one inspection per taxable year the
  default and requires a written notice to exceed it. 国税通則法第七十四条の十一
  第五項 permits further questioning *「新たに得られた情報に照らし非違があると
  認めるとき」* — an official's finding, not a written notice. **The defaults
  are opposite.**

The most valuable entries are absences that were searched for:

- **The United States has no general advance-notice-of-examination statute.**
  §7602(c) is notice of contacting *third parties*; §7521(b)(1) is an
  explanation given *"before or at an initial interview"* — and *at* is not
  *before*, and an interview is not the examination. Reading either as a
  general advance-notice rule would manufacture a taxpayer protection Congress
  did not enact. The facet is recorded `:read`, not `:silent`: someone looked.
- **国税通則法第七十四条の九第一項 lists seven items, not eleven.** Six are
  substantive and the seventh delegates to 政令. The figure quoted in practice
  counts items added by 国税通則法施行令第三十条の四, which this catalog has
  **not read**. Both figures are right about different instruments.
  `jp-notice-items` carries all three numbers and names the gap.
- **第百二十七条 is not the refusal penalty.** It punishes an official who
  leaks a secret learned on duty. Refusing an examination is 第百二十八条第二号.
  This was reached for first, from recollection, and it was wrong — so it is
  recorded, because the wrong article is the one a reader arrives with.

## The European Union has no power to examine anyone, and saying so is the entry

`[:eu]` is in the catalog and every examination action returns `:no-catalog`:

```clojure
(law/examination [:eu] :exam/field-visit {})
;; => {:examlaw/coverage :none :examlaw/reason :action-not-catalogued-here …}
```

Not because nobody looked — because someone looked and there is nothing to
read. Every cooperation article routes the actual power back to a Member
State: Article 11(1) *"in accordance with the procedural arrangements laid
down by the latter"*, Article 12a(2) *"in accordance with the laws and
procedural requirements of the Member State where the activities of a joint
audit take place."* The `:out-of-scope` entry carries that reason. Inventing
an EU-level examination power would be the most consequential error this
library could make.

Article 12a was also **measured, not inferred**: it is absent from the
2023-01-01 consolidation and present in the 2024-01-01 one. Both were fetched.
A catalog pinned to the wrong consolidation would report that joint audits do
not exist.

### Cross-border is the one place two jurisdictions must both be read

Article 12a(2): *"officials of another Member State shall not exercise any
powers that would exceed the scope of the powers granted to them under the
laws of their Member State."* The ceiling is the **lower** of two — so
`joint-audit` takes two jurisdictions and refuses if either is missing:

```clojure
(law/joint-audit [:jp] [:atlantis] :exam/inspect-books facts)
;; => {:examlaw/coverage :none
;;     :examlaw/unchecked [[:atlantis]]
;;     :examlaw/reason :cross-border-needs-both-jurisdictions}
```

An intersection with an unread set is not the unread set. Reporting the host's
rules alone would silently drop the home-state cap that the Article exists to
impose. `:eu/home-power-ceiling` is marked `:req/kind :two-jurisdiction` so it
can never be satisfied from a single-jurisdiction call.

## How much of the world is this? Ask, and supply the denominator

`world-coverage` **requires a universe and has no default**, for the same
reason `taxlaw`'s does. The thing being measured is precisely what this
catalog does not know about, so the denominator has to come from outside it.

```clojure
(law/world-coverage #{[:jp] [:us] [:eu] [:de] [:fr] [:sg] [:br]})
;; :examlaw/jurisdiction-fraction  [3 7]
;; :examlaw/facet-total            [20 98]   the figure that does not flatter
```

`20/98` is lower than `3/7`, and that is the point. The first draft of this
README wrote `30/98` from memory and the number was wrong — which is the same
failure mode the library is built to prevent, one level up.

| | `:read` | `:partly-read` | `:out-of-scope` | `:silent` | of |
|---|---|---|---|---|---|
| `[:jp]` | 11 | 1 | 0 | 2 | 14 |
| `[:us]` | 7 | 0 | 2 | 5 | 14 |
| `[:eu]` | 1 | 0 | 13 | 0 | 14 |
 `depth` partitions the
fourteen facets per jurisdiction into `:read`, `:partly-read`, `:out-of-scope`
and `:silent`, and a test asserts they sum to `:of` for every jurisdiction.

**`:silent` is a facet nobody has thought about**; `:out-of-scope` is one left
out on purpose, with the reason recorded. From every other view they are
identical — both answer `:no-catalog`, correctly, because neither is a pass.
This is the view that tells them apart, and the difference is whether there is
a decision behind the absence. United States closing procedure is `:silent`.
United States notice contents is `:out-of-scope`, because there is no general
advance notice for it to have contents.

A test makes this bidirectional: every facet marked `:out-of-scope` must
resolve to a recorded reason, and every recorded reason must belong to a facet
actually marked `:out-of-scope`. It found a defect on first run — `[:jp]`
carried a reason for `:exam/cross-border` saying the treaty instruments had
*not been read*, which is a silent facet, not a deliberate exclusion. The
reason moved to a `:facet/note` and the facet stayed `:silent`. `[:eu]`'s
thirteen out-of-scope facets share one `:out-of-scope-default` rather than
thirteen copies of the same sentence, and the test resolves through it.

`kotoba-lang/iso3166` is one place to get a universe of 193. This library does
**not** depend on it, deliberately: a library that shipped its own denominator
would be answering the one question the caller has to ask.

## What this is not

- **Not a tax administration system, and not a case-management system.** It
  selects nobody, opens nothing, assesses nothing and issues nothing. It has
  no store, no clock and no network.
- **Not a targeting or audit-selection tool.** There is no facility for
  scoring, ranking or shortlisting taxpayers, and adding one would require
  editing this repository — no input can reach such a thing at runtime.
- **Not legal advice.** Every claim here cites an instrument that can be
  fetched and checked. Route licensed work to counsel.

The nearest neighbours in this workspace, and the boundary with each:
`kotoba-lang/taxlaw` answers what a *taxpayer's document* must carry;
`cloud-itonami/cloud-itonami-isco-3352` is a tax-office documentation and
logistics robot with a structural no-enforcement-authority guarantee. This
library is the third thing: the *procedural preconditions on the state's own
power*, as a catalog, with a denominator.

## Run the tests

```bash
nbb --classpath "src:test" run-tests.cljs    # primary
clojure -M:test                              # JVM compat path, same file
```

18 tests, 372 assertions. The suite has been shown to fail in both directions,
by actually breaking the library and reading the count:

| change | result |
|---|---|
| unmodified | 0 failures |
| `authorized?` → `(empty? (:examlaw/unmet result))` | **21 failures** |
| `eval-clause` `:fact-true` on an absent key → `:no` | **6 failures** |

Both breaks were reverted and the suite returns to 0. A test that has only
ever been green is not evidence that it discriminates.
