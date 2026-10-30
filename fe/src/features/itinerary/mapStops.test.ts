import { describe, expect, it } from 'vitest'
import { DEMO_PLAN } from './demoPlan'
import { mapStops } from './mapStops'

describe('mapStops', () => {
  const day = DEMO_PLAN.days[0]

  it('numbers the stops in visiting order and marks meals', () => {
    const stops = mapStops(day)
    expect(stops.map((s) => s.order)).toEqual(day.items.map((_, i) => i + 1))
    expect(stops[0].name).toBe(day.items[0].name)
    expect(stops.some((s) => s.food)).toBe(day.items.some((i) => i.type === 'FOOD'))
  })

  it('skips items without coordinates', () => {
    const noCoords = { ...day, items: day.items.map((i) => ({ ...i, lat: 0, lon: 0 })) }
    expect(mapStops(noCoords)).toEqual([])
  })
})
