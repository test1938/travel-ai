import type { TripPlan } from '../../types/api'

/** Key of an item in a day: "2:danang-way-302872188". */
export function itemKey(dayIndex: number, placeId: string): string {
  return `${dayIndex}:${placeId}`
}

/** Items of `next` that were not on the same day in `previous` (new or moved): these get highlighted. */
export function changedItems(previous: TripPlan, next: TripPlan): Set<string> {
  const before = new Set(
    previous.days.flatMap((d) => d.items.map((i) => itemKey(d.dayIndex, i.placeId))),
  )
  const changed = new Set<string>()
  for (const day of next.days) {
    for (const item of day.items) {
      const key = itemKey(day.dayIndex, item.placeId)
      if (!before.has(key)) {
        changed.add(key)
      }
    }
  }
  return changed
}

/** Days whose list of places changed (added, removed or reordered). */
export function changedDays(previous: TripPlan, next: TripPlan): number[] {
  const signature = (plan: TripPlan) =>
    new Map(
      plan.days.map((d) => [d.dayIndex, d.items.map((i) => `${i.slot}:${i.placeId}`).join('|')]),
    )
  const before = signature(previous)
  return next.days
    .filter((d) => before.get(d.dayIndex) !== signature(next).get(d.dayIndex))
    .map((d) => d.dayIndex)
}
