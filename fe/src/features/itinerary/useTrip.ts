import { useQuery } from '@tanstack/react-query'
import type { ApiError } from '../../api/client'
import { tripKeys, tripsApi } from '../../api/trips'
import type { TripPlan } from '../../types/api'

/** Loads a saved trip; after a page refresh the plan comes back from the backend. */
export function useTrip(tripId: string) {
  return useQuery<TripPlan, ApiError>({
    queryKey: tripKeys.trip(tripId),
    queryFn: () => tripsApi.get(tripId),
    // A trip version never changes; new versions arrive as new data.
    staleTime: Infinity,
    retry: (count, error) => error.code !== 'NOT_FOUND' && count < 1,
  })
}
