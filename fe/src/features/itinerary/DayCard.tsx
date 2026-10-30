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

/** One day: heading with date and weather, its warnings, the stops in time order, then its map. */
export function DayCard({ day, sources, warnings, highlight }: DayCardProps) {
  return (
    <article
      aria-labelledby={`day-${day.dayIndex}`}
      className="overflow-hidden rounded-tp border border-line bg-white"
    >
      <header className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-4 pt-4 pb-3 sm:px-5">
        <h2 id={`day-${day.dayIndex}`} className="text-[18px] font-bold tracking-[-0.01em]">
          Ngày {day.dayIndex} · {formatDayHeading(day.date)}
        </h2>
        <span
          className={`text-[14px] ${day.weather?.rainy ? 'font-medium text-ink' : 'text-muted'}`}
        >
          {weatherText(day)}
        </span>
      </header>
      {warnings.length > 0 && (
        <ul className="mx-4 mb-3 space-y-1 rounded-tp border border-[#fde68a] bg-[#fffbeb] px-3 py-2 text-[13px] text-[#92400e] sm:mx-5">
          {warnings.map((w, i) => (
            <li key={`${w.code}-${i}`}>{w.message}</li>
          ))}
        </ul>
      )}
      {day.items.length === 0 ? (
        <p className="border-t border-line px-5 py-4 text-[15px] text-muted">
          Ngày tự do, chưa xếp điểm nào.
        </p>
      ) : (
        <ol className="border-t border-line">
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
      <div className="px-4 pb-4 sm:px-5">
        <DayMapSlot day={day} />
      </div>
    </article>
  )
}
