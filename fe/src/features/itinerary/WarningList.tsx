import type { Warning } from '../../types/api'
import { visibleWarnings, warningSeverity } from './itineraryModel'

const STYLE = {
  error: 'border-red-200 bg-red-50 text-red-800',
  warn: 'border-amber-200 bg-amber-50 text-amber-900',
  info: 'border-slate-200 bg-white text-slate-700',
} as const

/** Trip-level warnings (day-specific ones are shown on their day card); most severe first. */
export function WarningList({ warnings }: { warnings: Warning[] }) {
  const list = visibleWarnings(warnings).filter((w) => w.dayIndex == null)
  if (list.length === 0) {
    return null
  }
  return (
    <ul className="space-y-2" aria-label="Lưu ý">
      {list.map((w, i) => (
        <li
          key={`${w.code}-${i}`}
          className={`rounded-lg border p-3 text-sm ${STYLE[warningSeverity(w.code)]}`}
        >
          {w.message}
        </li>
      ))}
    </ul>
  )
}
