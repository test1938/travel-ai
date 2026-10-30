import { useEffect, useState } from 'react'
import { PLANNING_STEPS, serverStepStates, stepStates } from './planningSteps'

const MARK = {
  done: 'bg-ink',
  active: 'animate-pulse bg-sun',
  pending: 'bg-stone',
} as const

/**
 * Steps lighting up while the plan is being made, so a 10-20 s wait does not feel stuck. `step` is the step the
 * server reported last; until the first event arrives the progress is simulated.
 */
export function PlanningProgress({ step }: { step?: string | null }) {
  const [elapsed, setElapsed] = useState(0)

  useEffect(() => {
    const start = Date.now()
    const timer = setInterval(() => setElapsed(Date.now() - start), 300)
    return () => clearInterval(timer)
  }, [])

  const states = (step ? serverStepStates(step) : null) ?? stepStates(elapsed)
  return (
    <section data-ui className="rounded-tp bg-sand p-6" aria-live="polite">
      <h3 className="text-[22px] font-bold tracking-[-0.01em]">Đang lập lịch trình…</h3>
      <p className="mt-1 text-[15px] text-muted">
        Thường mất 10–20 giây. Đã {Math.floor(elapsed / 1000)} giây.
      </p>
      <ol className="mt-5">
        {PLANNING_STEPS.map((s, i) => (
          <li
            key={s.label}
            className="flex items-center gap-3 border-t border-line py-3 text-[16px] first:border-t-0"
          >
            <span className={`h-3 w-3 shrink-0 rounded-[2px] ${MARK[states[i]]}`} aria-hidden />
            <span className={states[i] === 'pending' ? 'text-faint' : 'text-ink'}>{s.label}</span>
            <span className="sr-only">
              {states[i] === 'done'
                ? ': xong'
                : states[i] === 'active'
                  ? ': đang làm'
                  : ': chưa tới'}
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}
