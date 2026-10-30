import type { TripPlan, TripRequest } from '../types/api'
import { api } from './client'

export const tripsApi = {
  create: (request: TripRequest) => api.post<TripPlan>('/api/v1/trips', request),
  /** Same as create, reporting each planning step (weather, candidates, retrieve, llm, assemble). */
  createWithProgress: (request: TripRequest, onStep: (step: string) => void) =>
    api.stream<TripPlan>('/api/v1/trips/stream', request, (e) => {
      if (e.event === 'step') {
        onStep((JSON.parse(e.data) as { step: string }).step)
      }
    }),
  get: (tripId: string) => api.get<TripPlan>(`/api/v1/trips/${encodeURIComponent(tripId)}`),
  version: (tripId: string, version: number) =>
    api.get<TripPlan>(`/api/v1/trips/${encodeURIComponent(tripId)}/versions/${version}`),
}

/** TanStack Query keys for trips. */
export const tripKeys = {
  trip: (tripId: string) => ['trip', tripId] as const,
  version: (tripId: string, version: number) => ['trip', tripId, 'version', version] as const,
}
