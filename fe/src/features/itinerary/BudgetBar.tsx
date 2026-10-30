import { budgetPercent, formatVnd } from '../../lib/format'
import { TYPE_LABELS } from '../../lib/labels'
import type { Budget, PlaceType } from '../../types/api'
import { budgetLevel } from './itineraryModel'

/** The meter carries the screen's one hue (sun yellow) while the plan fits; near and over are signals. */
const BAR_COLOR = { ok: 'bg-sun', near: 'bg-amber-500', over: 'bg-red-600' } as const

/** Total vs limit (computed by the backend, never by the model) with a split by type. */
export function BudgetBar({ budget, disclaimer }: { budget: Budget; disclaimer?: string }) {
  const percent = budgetPercent(budget.totalVnd, budget.limitVnd)
  const level = budgetLevel(budget)
  const left = budget.limitVnd - budget.totalVnd
  return (
    <section className="rounded-tp border border-line bg-white p-5" aria-labelledby="budget-title">
      <h2 id="budget-title" className="text-[16px] font-bold">
        Chi phí dự kiến
      </h2>
      <p className="mt-2 tabular-nums">
        <span
          className={`text-[28px] leading-none font-bold tracking-[-0.01em] ${level === 'over' ? 'text-red-700' : 'text-ink'}`}
        >
          {formatVnd(budget.totalVnd)}
        </span>
        <span className="text-[15px] text-muted"> / {formatVnd(budget.limitVnd)}</span>
      </p>
      <div
        className="mt-3 h-2.5 w-full overflow-hidden rounded-[2px] bg-stone"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.min(percent, 100)}
        aria-label={`Đã dùng ${percent}% ngân sách`}
      >
        <div
          className={`h-2.5 rounded-[2px] ${BAR_COLOR[level]}`}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>
      <p className="mt-2 text-[14px] text-muted tabular-nums">
        {level === 'over' ? `Đã dùng ${percent}%` : `Còn ${formatVnd(left)} (${100 - percent}%)`}
      </p>
      {level === 'over' && (
        <p role="alert" className="mt-3 rounded-tp bg-[#fdecea] p-3 text-[14px] text-[#7f1d1d]">
          Vượt ngân sách {formatVnd(budget.totalVnd - budget.limitVnd)}. Hãy bớt một hoạt động đắt
          hoặc tăng ngân sách.
        </p>
      )}
      <dl className="mt-4 text-[15px]">
        {(['ACTIVITY', 'FOOD'] as PlaceType[]).map((type) => (
          <div
            key={type}
            className="flex items-baseline justify-between border-t border-line py-2 first:border-t-0"
          >
            <dt className="text-muted">{TYPE_LABELS[type]}</dt>
            <dd className="tabular-nums">{formatVnd(budget.byType[type] ?? 0)}</dd>
          </div>
        ))}
      </dl>
      {disclaimer && <p className="mt-2 text-[13px] text-faint">{disclaimer}</p>}
    </section>
  )
}
