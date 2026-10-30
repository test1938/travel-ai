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

/** One stop: slot • time • name • type badge • price • reason • sources • "why recommended". */
export function PlanItemCard({ item, sources, highlighted = false }: PlanItemCardProps) {
  const cited = sourcesFor(item.sourceIds, sources)
  const stale = isPriceStale(item.verifyAt)
  return (
    <li
      className={`rounded-lg border p-3 transition-colors duration-700 ${
        highlighted
          ? 'border-amber-400 bg-amber-50 ring-2 ring-amber-300'
          : 'border-slate-200 bg-white'
      }`}
      data-highlighted={highlighted || undefined}
    >
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <span className="text-xs font-medium uppercase text-slate-500">
          {SLOT_LABELS[item.slot]}
        </span>
        <span className="text-sm tabular-nums text-slate-600">
          {item.start}–{item.end}
        </span>
        <span
          className={`rounded px-1.5 py-0.5 text-xs ${
            item.type === 'FOOD' ? 'bg-amber-100 text-amber-800' : 'bg-sky-100 text-sky-800'
          }`}
        >
          {TYPE_LABELS[item.type]}
        </span>
        {item.indoor && item.type === 'ACTIVITY' && (
          <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
            Trong nhà
          </span>
        )}
      </div>

      <div className="mt-1 flex flex-wrap items-baseline justify-between gap-2">
        <h4 className="font-medium text-slate-900">{item.name}</h4>
        <span
          className="text-sm font-medium tabular-nums text-slate-800"
          title={item.priceNote ?? undefined}
        >
          {priceLabel(item)}
        </span>
      </div>

      <p className="mt-1 text-sm text-slate-700">{item.reason}</p>

      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-500">
        <span>{formatDuration(item.durationMin)}</span>
        {item.verifyAt ? (
          <span className={stale ? 'text-amber-700' : undefined}>
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
        <details className="mt-2 text-xs text-slate-500">
          <summary className="cursor-pointer select-none">Ghi chú giá</summary>
          <p className="mt-1">{item.priceNote}</p>
        </details>
      )}

      {item.score && (
        <details className="mt-1 text-xs text-slate-500">
          <summary className="cursor-pointer select-none">
            Vì sao gợi ý? (điểm {Math.round(item.score.total * 100)}/100)
          </summary>
          <ul className="mt-2 space-y-1">
            {scoreRows(item.score).map((row) => (
              <li key={row.key} className="flex items-center gap-2">
                <span className="w-28 shrink-0">{row.label}</span>
                <span className="h-1.5 flex-1 rounded bg-slate-100">
                  <span
                    className="block h-1.5 rounded bg-sky-500"
                    style={{ width: `${row.percent}%` }}
                  />
                </span>
                <span className="w-8 text-right tabular-nums">{row.percent}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </li>
  )
}
