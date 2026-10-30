import { describe, expect, it } from 'vitest'
import { DEMO_PLAN } from '../itinerary/demoPlan'
import type { TripPlan } from '../../types/api'
import { changedDays, changedItems, itemKey } from './planDiff'

function withDay2Replaced(plan: TripPlan, placeIds: string[]): TripPlan {
  return {
    ...plan,
    version: plan.version + 1,
    days: plan.days.map((d) =>
      d.dayIndex !== 2
        ? d
        : {
            ...d,
            items: placeIds.map((id, i) => ({ ...d.items[i % d.items.length], placeId: id })),
          },
    ),
  }
}

describe('plan diff', () => {
  it('highlights only items that are new on their day', () => {
    const next = withDay2Replaced(DEMO_PLAN, [
      'danang-way-302872188', // kept
      'danang-node-5817580453', // new: 3D art museum
    ])
    expect(changedItems(DEMO_PLAN, next)).toEqual(new Set([itemKey(2, 'danang-node-5817580453')]))
    expect(changedDays(DEMO_PLAN, next)).toEqual([2])
  })

  it('finds nothing when the plan is the same', () => {
    expect(changedItems(DEMO_PLAN, DEMO_PLAN).size).toBe(0)
    expect(changedDays(DEMO_PLAN, DEMO_PLAN)).toEqual([])
  })
})
