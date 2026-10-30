import { lazy, Suspense, useSyncExternalStore } from 'react'
import type { DayPlan } from '../../types/api'
import { mapStops } from './mapStops'

const DayMap = lazy(() => import('./DayMap'))

const PLACEHOLDER = (
  <div className="h-48 w-full animate-pulse rounded-lg bg-slate-200" aria-hidden />
)

const noSubscription = () => () => {}

/** True in the browser, false while rendering on the server (and in renderToString tests). */
function useIsBrowser(): boolean {
  return useSyncExternalStore(
    noSubscription,
    () => true,
    () => false,
  )
}

/**
 * Loads Leaflet only in the browser (it needs `window`, and its own chunk keeps the first page small). Days
 * without coordinates get no map.
 */
export function DayMapSlot({ day }: { day: DayPlan }) {
  const isBrowser = useIsBrowser()
  if (mapStops(day).length === 0) {
    return null
  }
  return (
    <div className="mt-3">
      {isBrowser ? (
        <Suspense fallback={PLACEHOLDER}>
          <DayMap day={day} />
        </Suspense>
      ) : (
        PLACEHOLDER
      )}
    </div>
  )
}
