# Write-up: AI Proposal Triage (MATACS-TH-2026-02)

## The flow in one paragraph

Reviewer uploads proposals and picks a category. Code loads the category's current criteria (a frozen, versioned copy) and extracts the text of each file. An AI step turns each proposal's text into a fixed set of facts, each with a quote from the document. Code then runs the pass/fail checks, computes the weighted score and sorts. A second, small AI step writes a one-line reason per position from the numbers. The reviewer checks the list, can override with a written reason, and a person makes the award. See `diagram/flow.png`.

## 1. AI vs. rules

**AI** is used only where the input is messy language: (a) pulling facts out of free-text proposals (licence stated or not, number of installs, price, weeks, warranty years, team items), and (b) phrasing the one-line reason. It also reports if a document contains text that tries to instruct the reviewer system.

**Plain code** does everything that has a right answer: reading the admin's criteria, pass/fail on mandatory items, budget and schedule limits, the scoring formulas, the weights, sorting and tie-breaking. I draw the line there because arithmetic and thresholds must be exact, repeatable and testable, and an admin must be able to change them without touching a prompt.

## 2. Consistency and explainability

- The AI never produces a score, only facts. Scores come from fixed formulas (`prototype/Score.java`), so the same facts and criteria always give the same number.
- The extraction step runs at temperature 0 with a strict JSON schema. Two runs can still differ slightly in wording, so I run extraction twice and send any field that differs to the reviewer, instead of silently picking one.
- Every extracted fact carries a quote. If the quote cannot be found in the document text, the fact is rejected and shown as "not stated".
- The reviewer sees, per proposal: pass/fail results, a score for each criterion with the quote behind it, and the weighted total. "Why is B above C" can be answered by reading two rows.
- So yes, ranking the same proposals twice gives the same result, apart from rare extraction differences that are surfaced rather than hidden.

## 3. Human in the loop

Three checkpoints:
1. **After extraction:** facts with a missing quote, a conflict between two runs, or an odd value (for example a price far below the others) are shown for confirmation.
2. **Flagged and ineligible items:** the reviewer must look at every proposal that is flagged (missing information, suspicious content) or excluded, and can reinstate one with a reason.
3. **Before award:** the reviewer can override any score or position, with a mandatory comment. The tool only recommends; a person decides the award.

Audit log (append-only): who uploaded and who reviewed, timestamp, criteria version and weights, prompt version, model name, a hash of each input file, raw extracted facts, computed scores, each override (old value, new value, reason, person) and the final decision. Proposal text is stored under access control, not copied into the log.

## 4. Changing criteria

Each scoring run stores the criteria version it used. Editing criteria creates a **new version**; old results are never changed in place. The reviewer sees a banner "scored with version 1, current is version 2" and can start a re-score. Re-scoring is cheap and needs no new AI calls, because the extracted facts are stored and only the formulas run again. The tool shows old and new ranking side by side, with who changed what. If a change adds a criterion that needs a fact that was never extracted, only that fact is extracted again. Awards already made keep the version they were decided under.

## 5. Risks and safeguards

| Risk | Guard |
|---|---|
| Wrong extraction (misread number, wrong year) | Quote required for every fact; code checks the quote exists and the number matches; twice-run comparison; reviewer confirmation on conflicts |
| Made-up facts | AI may only answer from the document and must answer "not stated" otherwise; missing data scores 0 and is flagged, never guessed |
| Manipulation (text inside a proposal telling the system to rank it first, hidden text) | Proposal text is wrapped as untrusted data and the prompt says to ignore instructions inside it; extractor returns an `instruction_like_text` field; code flags any such proposal and does not auto-rank it; it cannot change scores because scores come from code. Sample C is this case. |
| Mandatory requirement not really met (for example a promise to hire a licensed subcontractor later) | The criterion says the bidder holds the licence and states it; the extractor records who holds it; promises of future compliance fail. Sample B is this case, so its low price is irrelevant |
| Missing information (no schedule in D) | Scored 0 for that criterion, marked, and reviewer is prompted to ask the bidder |
| Bias (favouring big firms, polished writing, certain names) | Scores use only extracted facts and numbers, not tone or style; the company name is not an input to scoring; periodic check that decisions are not skewed by bidder type |
| Cost | AI is used once per proposal for extraction (cheap), results are cached by file hash, re-scoring needs no AI; reason text uses a small model |
| Data privacy | Only supplied proposals are sent; no personal data in logs; provider data-retention settings checked before real use |

## Assumptions

- Each sample is treated as extracted PDF text; only the fictional samples are used.
- Scoring formulas are my own simple choices, which an admin would tune per category.
