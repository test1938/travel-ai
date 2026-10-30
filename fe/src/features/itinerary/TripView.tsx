import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
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

/** The itinerary layout: day cards on the left, budget and warnings on the right (stacked on mobile). */
export function TripView({ plan, highlight, header, aside }: TripViewProps) {
  const warnings = visibleWarnings(plan.warnings)
  return (
    <div className="mx-auto grid w-full max-w-5xl gap-6 lg:grid-cols-[1fr_320px]">
      <div className="space-y-4">
        <header>
          <h2 className="text-xl font-semibold">{plan.title}</h2>
          {plan.summary && <p className="mt-1 text-slate-700">{plan.summary}</p>}
          <p className="mt-1 text-xs text-slate-500">
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
            className="inline-block rounded-full border border-sky-600 px-3 py-1 text-sm text-sky-700 lg:hidden"
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
          className="block rounded-lg border border-slate-300 bg-white px-4 py-2 text-center text-sm font-medium hover:border-sky-400"
        >
          Lập lịch khác
        </Link>
      </aside>
    </div>
  )
}
