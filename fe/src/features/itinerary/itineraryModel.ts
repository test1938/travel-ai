import { budgetPercent, daysSince } from '../../lib/format'
import { SCORE_LABELS } from '../../lib/labels'
import type { Budget, ScoreBreakdown, Source, Warning } from '../../types/api'

/** Prices checked longer ago than this are flagged as possibly outdated (plan/01-dataset.md §3). */
export const STALE_PRICE_DAYS = 180

export function isPriceStale(verifyAt: string | null, today: Date = new Date()): boolean {
  const age = daysSince(verifyAt, today)
  return age !== null && age > STALE_PRICE_DAYS
}

export type BudgetLevel = 'ok' | 'near' | 'over'

/** ok < 90%, near 90-100%, over > 100% (or flagged by the backend). */
export function budgetLevel(budget: Budget): BudgetLevel {
  if (budget.overBudget) {
    return 'over'
  }
  return budgetPercent(budget.totalVnd, budget.limitVnd) >= 90 ? 'near' : 'ok'
}

export type WarningSeverity = 'error' | 'warn' | 'info'

const SEVERITY: Record<string, WarningSeverity> = {
  OVER_BUDGET: 'error',
  CHILD_AGE: 'error',
  FALLBACK_USED: 'warn',
  RAIN_OUTDOOR: 'warn',
  CLOSED_AT_SLOT: 'warn',
  CANDIDATES_SHORT: 'warn',
  FOOD_SHORT: 'warn',
  CANDIDATES_RELAXED: 'info',
  FOOD_REPEATED: 'info',
  MEAL_SLOT: 'info',
  DAYTRIP_LUNCH: 'info',
  WEATHER_UNAVAILABLE: 'info',
}

export function warningSeverity(code: string): WarningSeverity {
  return SEVERITY[code] ?? 'info'
}

const ORDER: Record<WarningSeverity, number> = { error: 0, warn: 1, info: 2 }

/** Warnings to list (most severe first); the price disclaimer is shown separately under the budget. */
export function visibleWarnings(warnings: Warning[]): Warning[] {
  return warnings
    .filter((w) => w.code !== 'PRICE_ESTIMATE_DISCLAIMER')
    .map((w, i) => ({ w, i }))
    .sort(
      (a, b) => ORDER[warningSeverity(a.w.code)] - ORDER[warningSeverity(b.w.code)] || a.i - b.i,
    )
    .map(({ w }) => w)
}

export function disclaimer(warnings: Warning[]): string | undefined {
  return warnings.find((w) => w.code === 'PRICE_ESTIMATE_DISCLAIMER')?.message
}

/** Score components as labelled 0-100 values for "Vì sao gợi ý?" */
export function scoreRows(
  score: ScoreBreakdown,
): { key: keyof typeof SCORE_LABELS; label: string; percent: number }[] {
  return (Object.keys(SCORE_LABELS) as (keyof typeof SCORE_LABELS)[]).map((key) => ({
    key,
    label: SCORE_LABELS[key],
    percent: Math.round(score[key] * 100),
  }))
}

/** Sources cited by an item, in the item's order; unknown ids are ignored. */
export function sourcesFor(ids: string[], sources: Source[]): Source[] {
  const byId = new Map(sources.map((s) => [s.id, s]))
  return ids.map((id) => byId.get(id)).filter((s): s is Source => s !== undefined)
}

/** Only http(s) links are rendered as anchors (data comes from external sources). */
export function safeUrl(url: string | null): string | null {
  if (!url) {
    return null
  }
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.toString() : null
  } catch {
    return null
  }
}
