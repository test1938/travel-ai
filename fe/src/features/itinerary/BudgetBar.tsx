import { budgetPercent, formatVnd } from '../../lib/format'
import { TYPE_LABELS } from '../../lib/labels'
import type { Budget, PlaceType } from '../../types/api'
import { budgetLevel } from './itineraryModel'

const BAR_COLOR = { ok: 'bg-emerald-500', near: 'bg-amber-500', over: 'bg-red-600' } as const

/** Total vs limit (computed by the backend, never by the model) with a split by type. */
export function BudgetBar({ budget, disclaimer }: { budget: Budget; disclaimer?: string }) {
  const percent = budgetPercent(budget.totalVnd, budget.limitVnd)
  const level = budgetLevel(budget)
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4" aria-label="Ngân sách">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-semibold">Chi phí dự kiến</h3>
        <p
          className={`text-sm tabular-nums ${level === 'over' ? 'font-semibold text-red-700' : 'text-slate-700'}`}
        >
          {formatVnd(budget.totalVnd)} / {formatVnd(budget.limitVnd)}
        </p>
      </div>
      <div
        className="mt-2 h-3 w-full overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.min(percent, 100)}
        aria-label={`Đã dùng ${percent}% ngân sách`}
      >
        <div
          className={`h-3 rounded-full ${BAR_COLOR[level]}`}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>
      {level === 'over' && (
        <p role="alert" className="mt-2 rounded-md bg-red-50 p-2 text-sm text-red-800">
          Vượt ngân sách {formatVnd(budget.totalVnd - budget.limitVnd)}. Hãy bớt một hoạt động đắt
          hoặc tăng ngân sách.
        </p>
      )}
      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
        {(['ACTIVITY', 'FOOD'] as PlaceType[]).map((type) => (
          <div key={type} className="rounded-md bg-slate-50 p-2">
            <dt className="text-xs text-slate-500">{TYPE_LABELS[type]}</dt>
            <dd className="tabular-nums">{formatVnd(budget.byType[type] ?? 0)}</dd>
          </div>
        ))}
      </dl>
      {disclaimer && <p className="mt-3 text-xs text-slate-500">{disclaimer}</p>}
    </section>
  )
}
