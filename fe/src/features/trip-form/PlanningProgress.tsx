import { useEffect, useState } from 'react'
import { PLANNING_STEPS, serverStepStates, stepStates } from './planningSteps'

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
    <div className="rounded-xl border border-slate-200 bg-white p-5" aria-live="polite">
      <p className="mb-3 font-medium">Đang lập lịch trình…</p>
      <ol className="space-y-2">
        {PLANNING_STEPS.map((step, i) => (
          <li key={step.label} className="flex items-center gap-3 text-sm">
            <span
              className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-xs ${
                states[i] === 'done'
                  ? 'bg-emerald-500 text-white'
                  : states[i] === 'active'
                    ? 'animate-pulse bg-sky-500 text-white'
                    : 'bg-slate-200 text-slate-500'
              }`}
              aria-hidden
            >
              {states[i] === 'done' ? '✓' : i + 1}
            </span>
            <span className={states[i] === 'pending' ? 'text-slate-400' : 'text-slate-800'}>
              {step.label}
            </span>
          </li>
        ))}
      </ol>
      <p className="mt-4 text-xs text-slate-500">
        Thường mất 10–20 giây. {Math.floor(elapsed / 1000)} giây đã trôi qua.
      </p>
    </div>
  )
}
