# MATACS-TH-2026-02: AI Proposal Triage Design

- **Name:** Muhammad Sheeraz
- **Reference:** MATACS-TH-2026-02
- **Time actually spent:** About 3 hours (reading the brief, understanding and reviewing the AI-assisted draft, and editing it)

## Files

| File | What it is |
|---|---|
| [`diagram/flow.png`](diagram/flow.png) | Process flow from upload to ranked output, with AI steps, rule steps and human checkpoints |
| [`WRITEUP.md`](WRITEUP.md) | Answers to the five questions |
| [`PROMPTS.md`](PROMPTS.md) | The extraction prompt and the reason prompt, as they would be sent |
| [`ranking.json`](ranking.json) | Expected ranking of proposals A to E |
| [`prototype/`](prototype/) | Optional: the rule-based half (pass/fail, scoring, ranking) as a small runnable program, in Java and in JavaScript |

## Design in short

AI only reads messy text (extracts facts with quotes) and phrases a one-line reason. Everything with a right answer (pass/fail, weights, scores, sorting) is plain code, so the same facts always give the same ranking. A person reviews flagged items, can override any result with a reason, and makes the award. Details are in the write-up.

## Prototype

```
cd prototype
java Score.java     # JDK 17 or newer
node score.js       # Node 18 or newer, same logic in JavaScript
```

I work in both Java and JavaScript, so the prototype is written in both languages. Both versions give identical output (checked by comparing the two). Neither needs libraries or API keys. `Score.java` holds the criteria and the facts a verified extraction step would produce for A to E (entered by hand), applies the pass/fail rules and the weighted formulas, and prints the ranking. The AI extraction step itself is described in `PROMPTS.md` but not implemented, so it was not run against a model. `ranking.json` keeps the scores from this program with hand-written reasons.

## How I used AI tools

I used Claude (through Claude Code) to help draft the flow, the write-up, the prompts and the scoring program, and I asked for the prototype in Java and JavaScript instead of Python because those are the languages I work in. I did not accept its output as it came. Its first diagram coloured a plain-code step as an AI step and an AI step as a human checkpoint, which was caught when the rendered image was checked and fixed, and its automatically generated one-line reasons ("strongest on price") were too thin for a reviewer, so `ranking.json` uses hand-written reasons that name the real gap, such as the missing licence in B. I also kept all scoring in plain code rather than asking the AI to score, so the same facts always give the same ranking, and the AI is limited to extracting facts with quotes and phrasing the reason.
