import type { Warning } from '../../types/api'
import { visibleWarnings, warningSeverity } from './itineraryModel'

const STYLE = {
  error: 'bg-[#fdecea] text-[#7f1d1d]',
  warn: 'border border-[#fde68a] bg-[#fffbeb] text-[#92400e]',
  info: 'border border-line bg-white text-ink',
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
          className={`rounded-tp p-3 text-[14px] ${STYLE[warningSeverity(w.code)]}`}
        >
          {w.message}
        </li>
      ))}
    </ul>
  )
}
