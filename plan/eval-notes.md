# Eval notes (Phase 8)

How to run, what was measured, what changed. Reports live in `be/eval/results/` (Markdown + CSV, header with git
sha, model, prompt versions and config). Numbers are copied from those files, not rounded up.

## How to run

| Command (from `be/`) | Needs | What it measures |
|---|---|---|
| `./mvnw test -Dgroups=eval -Dtest=OfflineEvalRunner [-Deval.label=x]` | nothing | The safety net only: every planner call fails, plans come from candidates + greedy fallback. Chat cases are skipped. |
| `./mvnw test -Dgroups=eval -Dtest=EvalRunner [-Deval.label=x] [-Deval.judge=false]` | Docker + `AI_API_KEY` | The real system with Gemini: orchestrator, TripService, ChatService; judge-v1 scores. |
| `./mvnw test -Dgroups=eval -Dtest=RetrievalEvalRunner` | Docker + `AI_API_KEY` | Retrieval hit@k / MRR on the golden set (Phase 3). |

Cases: `be/eval/cases.json`, 20 cases (happy 6, hard 4, modify 4, ask 3, attack 3), validated in the normal build
by `EvalCaseTest`. Hard rules (schema, ids, child age, pace, budget, locked days) must hold for every final plan;
expectations (must include / must not include, intent, refusal, sources, mentions, latency, tokens) say what a
good answer looks like.

## Baseline → fix → re-run (offline, no model)

| Metric | Baseline `cc589b2` | After fix `0e59dcf` |
|---|---|---|
| Report | `2026-10-30-offline-baseline.md` | `2026-10-30-offline-after-fix.md` |
| Plan cases done | 10/10 | 10/10 |
| Hard rules pass | 10/10 (100%) | 10/10 (100%) |
| Expectations hit | 8/10 (80%) | 9/10 (90%) |
| HAPPY expectations | 5/6 | 6/6 |
| HARD expectations | 3/4 | 3/4 |
| Plan latency p50 / p95 | 17 ms / 314 ms | about the same (no model) |

Fixes made from the baseline:

1. **Hội An could never be planned by the fallback** (`happy-dn-hoian-group`). It is a 300-minute day trip, best
   in the afternoon, open 08:00–17:00. Started after lunch it cannot end before closing; started in the morning it
   was rejected because of its best time. Long visits now always fit the morning slot (they fill the day anyway).
   Regression test: `GreedyFallbackPlannerTest.afternoonDayTripThatClosesEarlyStartsInTheMorning`.
2. **Notes are silently ignored by the fallback** (`hard-note-no-hiking`: "mother cannot climb" still got Sơn Trà
   peninsula). The fallback cannot read free text, so this case still fails offline by design; plans from the
   fallback now carry a `NOTES_NOT_APPLIED` warning that quotes the notes, so the traveler knows to check.

## Insights

- **The safety net needs its own eval.** All unit tests passed while one of the best-known places in the catalog
  was unreachable for the fallback. The bug was in the interaction of three data fields (duration, best time,
  opening hours) with one scheduling rule; only an end-to-end case with a concrete expectation found it. The LLM
  path was not affected because best time is a hint there, not a rule, so the problem only shows when the model
  fails, which is exactly when nobody is looking.
- **Free text is the one constraint only the model can honour.** Child age, budget, pace and locked days are
  enforced in code and pass 10/10 even without a model; notes are not. That makes notes the first thing to check
  in the real-model run (does Gemini respect "no hiking"?) and the strongest argument for keeping the fallback
  honest in the UI rather than pretending it understood.

## Still to do with the real model (👤 needs Docker + key)

1. Run `EvalRunner` → `be/eval/results/<date>-baseline.md` (schema first try, repair and fallback rates, p50/p95,
   tokens, cost per plan, judge scores, all 20 cases).
2. Fix the 2–3 biggest failures it shows (prompt, scorer weights or retrieval config), re-run with
   `-Deval.label=after-<change>`, add a before/after table here.
3. Judge calibration (T8.06): score 5 cases by hand and compare.

| Case | Judge G | Human G | Judge R | Human R | Note |
|---|---|---|---|---|---|
| | | | | | |
