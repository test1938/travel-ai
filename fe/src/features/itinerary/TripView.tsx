import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { formatVnd } from '../../lib/format'
import type { TripPlan } from '../../types/api'
import { BudgetBar } from './BudgetBar'
import { DayCard } from './DayCard'
import { disclaimer, visibleWarnings } from './itineraryModel'
import { WarningList } from './WarningList'

interface TripViewProps {
  plan: TripPlan
  /** Item keys "day:placeId" to emphasise (new after a chat change). */
  highlight?: Set<string>
  /** Shown above the days, e.g. the version switcher or a change banner. */
  header?: ReactNode
  /** Extra content at the top of the side column, e.g. the chat. */
  aside?: ReactNode
}

/**
 * The itinerary screen. Its question: "where do we go each day, and does it fit the budget?" The days are the
 * biggest block; the right rail (384px) holds the chat, the budget and the trip-level warnings.
 * Grid: 744 + 24 + 384 = 1152px, centred; stacked below 1024px.
 */
export function TripView({ plan, highlight, header, aside }: TripViewProps) {
  const warnings = visibleWarnings(plan.warnings)
  const stops = plan.days.reduce((n, d) => n + d.items.length, 0)
  return (
    <div className="mx-auto grid w-full max-w-[1152px] gap-6 lg:grid-cols-[minmax(0,1fr)_384px]">
      <div className="min-w-0 space-y-4">
        <header className="pb-2">
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">{plan.title}</h1>
          <p className="mt-1.5 text-[16px] text-muted tabular-nums">
            {plan.days.length} ngày · {stops} điểm dừng · {formatVnd(plan.budget.totalVnd)} trên{' '}
            {formatVnd(plan.budget.limitVnd)}
          </p>
          {plan.summary && <p className="mt-2 max-w-[68ch] text-[16px]">{plan.summary}</p>}
          <p className="mt-2 text-[13px] text-faint">
            Phiên bản {plan.version}
            {plan.meta.fallback
              ? ' · xếp tự động (không dùng AI)'
              : plan.meta.repaired
                ? ' · AI đã tự sửa lỗi'
                : ''}
          </p>
        </header>
        {header}
        {aside && (
          <a
            href="#trip-aside"
            className="inline-flex min-h-10 items-center rounded-tp bg-ink px-4 text-[15px] font-medium text-white lg:hidden"
          >
            Hỏi hoặc chỉnh lịch bằng chat ↓
          </a>
        )}
        {plan.days.map((day) => (
          <DayCard
            key={day.dayIndex}
            day={day}
            sources={plan.sources}
            warnings={warnings.filter((w) => w.dayIndex === day.dayIndex)}
            highlight={highlight}
          />
        ))}
      </div>
      <aside id="trip-aside" className="scroll-mt-4 space-y-4 lg:sticky lg:top-4 lg:self-start">
        {aside}
        <BudgetBar budget={plan.budget} disclaimer={disclaimer(plan.warnings)} />
        <WarningList warnings={plan.warnings} />
        <Link
          to="/"
          className="flex min-h-10 items-center justify-center rounded-tp border border-control bg-white px-4 text-[15px] font-medium transition-colors hover:border-ink"
        >
          Lập lịch khác
        </Link>
      </aside>
    </div>
  )
}
