import type { TripPlan, TripRequest } from '../types/api'
import { api } from './client'

export const tripsApi = {
  create: (request: TripRequest) => api.post<TripPlan>('/api/v1/trips', request),
  get: (tripId: string) => api.get<TripPlan>(`/api/v1/trips/${encodeURIComponent(tripId)}`),
  version: (tripId: string, version: number) =>
    api.get<TripPlan>(`/api/v1/trips/${encodeURIComponent(tripId)}/versions/${version}`),
}

/** TanStack Query keys for trips. */
export const tripKeys = {
  trip: (tripId: string) => ['trip', tripId] as const,
}
