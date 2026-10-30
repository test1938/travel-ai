import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ApiError } from '../../api/client'
import { tripKeys, tripsApi } from '../../api/trips'
import type { TripPlan, TripRequest } from '../../types/api'

/** Creates a trip and seeds the trip cache so the itinerary page renders without a second request. */
export function useCreateTrip() {
  const queryClient = useQueryClient()
  return useMutation<TripPlan, ApiError, TripRequest>({
    mutationFn: tripsApi.create,
    onSuccess: (plan) => queryClient.setQueryData(tripKeys.trip(plan.tripId), plan),
  })
}
