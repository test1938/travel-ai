import { formatDayHeading } from '../../lib/format'
import type { DayPlan, Source, Warning } from '../../types/api'
import { DayMapSlot } from './DayMapSlot'
import { PlanItemCard } from './PlanItemCard'

interface DayCardProps {
  day: DayPlan
  sources: Source[]
  warnings: Warning[]
  /** Item keys "day:placeId" to emphasise. */
  highlight?: Set<string>
}

function weatherText(day: DayPlan): string {
  if (!day.weather) {
    return 'Chưa có dự báo'
  }
  const temp =
    day.weather.tempMinC != null && day.weather.tempMaxC != null
      ? ` · ${Math.round(day.weather.tempMinC)}–${Math.round(day.weather.tempMaxC)}°C`
      : ''
  return `${day.weather.rainy ? 'Dự báo mưa' : (day.weather.summary ?? 'Không mưa')}${temp}`
}

/** One day: heading with date and weather, its warnings, then the stops in time order. */
export function DayCard({ day, sources, warnings, highlight }: DayCardProps) {
  return (
    <article className="rounded-xl border border-slate-200 bg-slate-50 p-4">
      <header className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-base font-semibold">
          Ngày {day.dayIndex} · {formatDayHeading(day.date)}
        </h3>
        <span className={`text-sm ${day.weather?.rainy ? 'text-sky-700' : 'text-slate-500'}`}>
          {weatherText(day)}
        </span>
      </header>
      {warnings.length > 0 && (
        <ul className="mb-3 space-y-1 text-xs text-amber-800">
          {warnings.map((w, i) => (
            <li key={`${w.code}-${i}`}>• {w.message}</li>
          ))}
        </ul>
      )}
      {day.items.length === 0 ? (
        <p className="text-sm text-slate-500">Ngày tự do.</p>
      ) : (
        <ol className="space-y-2">
          {day.items.map((item) => (
            <PlanItemCard
              key={`${item.placeId}-${item.start}`}
              item={item}
              sources={sources}
              highlighted={highlight?.has(`${day.dayIndex}:${item.placeId}`)}
            />
          ))}
        </ol>
      )}
      <DayMapSlot day={day} />
    </article>
  )
}
