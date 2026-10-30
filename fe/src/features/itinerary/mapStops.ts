import type { DayPlan } from '../../types/api'

export interface MapStop {
  order: number
  name: string
  lat: number
  lon: number
  food: boolean
}

/** Stops of a day with usable coordinates, numbered in visiting order (items are already in time order). */
export function mapStops(day: DayPlan): MapStop[] {
  return day.items
    .filter(
      (i) => Number.isFinite(i.lat) && Number.isFinite(i.lon) && !(i.lat === 0 && i.lon === 0),
    )
    .map((i, index) => ({
      order: index + 1,
      name: i.name,
      lat: i.lat,
      lon: i.lon,
      food: i.type === 'FOOD',
    }))
}
