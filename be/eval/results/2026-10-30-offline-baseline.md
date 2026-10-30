# Eval offline-baseline (2026-10-30)

| | |
|---|---|
| Mode | offline-fallback |
| Model | none (greedy fallback) |
| Git | `03c60f5` |
| Prompts | ask-v3, change-v1, intent-v1, judge-v1, planner-v1, repair-v1, replan-v1, tools-v1 |
| Config | topK=5, threshold=0.45, maxRepairs=2, retries=0, chatTimeout=30s, weather=off, judge=off |

## Summary

| Metric | Value |
|---|---|
| Cases done / error / skipped | 10 / 0 / 10 of 20 |
| Hard rules pass (final plan) | 10/10 (100%) |
| Expectations hit | 8/10 (80%) |
| Schema valid on first try (plan cases) | 0/10 (0%) |
| Repaired (plan cases) | 0/10 (0%) |
| Fallback used (plan cases) | 10/10 (100%) |
| Plan latency p50 / p95 | 17 ms / 314 ms |
| Chat latency p50 / p95 | n/a |
| Tokens per plan (avg in / out) | 0.0 / 0.0 |
| Estimated cost per plan | n/a (no priced model calls) |
| Judge groundedness (avg 1-5) | n/a |
| Judge reason quality / helpfulness (avg 1-5) | n/a |

## By category

| Category | Done | Hard pass | Expectations hit |
|---|---|---|---|
| HAPPY | 6/6 | 6/6 (100%) | 5/6 (83%) |
| HARD | 4/4 | 4/4 (100%) | 3/4 (75%) |
| MODIFY | 0/4 | n/a | n/a |
| ASK | 0/3 | n/a | n/a |
| ATTACK | 0/3 | n/a | n/a |

## Cases

| Case | Status | Hard | Expect | ms | Tokens | Repairs | Fallback | Judge G/R | Failed checks / error |
|---|---|---|---|---|---|---|---|---|---|
| happy-dn-family | DONE | yes | yes | 314 | 0 | 0 | true |  |  |
| happy-hn-culture | DONE | yes | yes | 22 | 0 | 0 | true |  |  |
| happy-qn-halong | DONE | yes | yes | 14 | 0 | 0 | true |  |  |
| happy-dn-food-night | DONE | yes | yes | 17 | 0 | 0 | true |  |  |
| happy-hn-family-kids | DONE | yes | yes | 22 | 0 | 0 | true |  |  |
| happy-dn-hoian-group | DONE | yes | **no** | 21 | 0 | 0 | true |  | mustIncludeAnyOf: none of [danang-node-4601279489] |
| hard-tight-budget | DONE | yes | yes | 11 | 0 | 0 | true |  |  |
| hard-toddler-qn | DONE | yes | yes | 13 | 0 | 0 | true |  |  |
| hard-hn-long-toddler | DONE | yes | yes | 22 | 0 | 0 | true |  |  |
| hard-note-no-hiking | DONE | yes | **no** | 13 | 0 | 0 | true |  | mustNotInclude: contains [danang-node-1623183649] |
| modify-budget | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| modify-exclude-bana | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| modify-day2-indoor | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| modify-add-seafood | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| ask-bana-kids | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| ask-hn-clothes | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| ask-qn-cruise | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| attack-extract-prompt | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| attack-bar-with-kid | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
| attack-write-tool | SKIPPED |  |  |  |  |  |  |  | not supported by offline-fallback |
