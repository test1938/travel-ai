import { formatDate, formatDuration, priceLabel } from '../../lib/format'
import { SLOT_LABELS, TYPE_LABELS } from '../../lib/labels'
import type { PlanItem, Source } from '../../types/api'
import { isPriceStale, scoreRows, sourcesFor } from './itineraryModel'
import { SourceChip } from './SourceChip'

interface PlanItemCardProps {
  item: PlanItem
  sources: Source[]
  /** Briefly emphasised after a chat change added this stop. */
  highlighted?: boolean
}

/**
 * One stop as a row of the day: the time on the left; name and price on one line, then the reason and the
 * evidence (duration, price date, sources, "why recommended").
 */
export function PlanItemCard({ item, sources, highlighted = false }: PlanItemCardProps) {
  const cited = sourcesFor(item.sourceIds, sources)
  const stale = isPriceStale(item.verifyAt)
  return (
    <li
      className={`grid grid-cols-[76px_minmax(0,1fr)] gap-x-4 border-t border-line px-4 py-4 transition-colors duration-700 first:border-t-0 sm:grid-cols-[96px_minmax(0,1fr)] sm:px-5 ${
        highlighted ? 'bg-sun-pale' : 'bg-white'
      }`}
      data-highlighted={highlighted || undefined}
    >
      <div className="pt-0.5">
        <span className="block text-[13px] text-muted">{SLOT_LABELS[item.slot]}</span>
        <span className="block text-[15px] font-medium tabular-nums">
          {item.start}–{item.end}
        </span>
      </div>

      <div className="min-w-0">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <h3 className="text-[17px] leading-snug font-medium">{item.name}</h3>
          <span className="text-[16px] font-bold tabular-nums" title={item.priceNote ?? undefined}>
            {priceLabel(item)}
          </span>
        </div>

        <div className="mt-1.5 flex flex-wrap gap-1.5">
          <span
            className={`rounded-[2px] px-1.5 py-px text-[12px] ${
              item.type === 'FOOD' ? 'bg-sun-pale text-[#5c4300]' : 'bg-stone text-ink'
            }`}
          >
            {TYPE_LABELS[item.type]}
          </span>
          {item.indoor && item.type === 'ACTIVITY' && (
            <span className="rounded-[2px] bg-sand px-1.5 py-px text-[12px] text-ink">
              Trong nhà
            </span>
          )}
        </div>

        <p className="mt-2 text-[15px] leading-[1.5]">{item.reason}</p>

        <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1.5 text-[13px] text-faint">
          <span>{formatDuration(item.durationMin)}</span>
          {item.verifyAt ? (
            <span className={stale ? 'text-[#92400e]' : undefined}>
              · Giá cập nhật {formatDate(item.verifyAt)}
              {stale ? ' (có thể đã cũ)' : ''}
            </span>
          ) : (
            item.costEstimated && <span>· Giá ước tính</span>
          )}
          {cited.map((s) => (
            <SourceChip key={s.id} source={s} />
          ))}
        </div>

        {item.priceNote && (
          <details className="mt-2 text-[13px] text-muted">
            <summary className="cursor-pointer select-none hover:text-ink">Ghi chú giá</summary>
            <p className="mt-1">{item.priceNote}</p>
          </details>
        )}

        {item.score && (
          <details className="mt-1 text-[13px] text-muted">
            <summary className="cursor-pointer select-none hover:text-ink">
              Vì sao gợi ý? (điểm {Math.round(item.score.total * 100)}/100)
            </summary>
            <ul className="mt-2 space-y-1.5">
              {scoreRows(item.score).map((row) => (
                <li key={row.key} className="flex items-center gap-2">
                  <span className="w-28 shrink-0">{row.label}</span>
                  <span className="h-1.5 flex-1 rounded-[2px] bg-stone">
                    <span
                      className="block h-1.5 rounded-[2px] bg-ink"
                      style={{ width: `${row.percent}%` }}
                    />
                  </span>
                  <span className="w-8 text-right tabular-nums">{row.percent}</span>
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </li>
  )
}
