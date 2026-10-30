import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { ApiError } from '../../api/client'
import { tripKeys, tripsApi } from '../../api/trips'
import type { TripPlan, TripRequest } from '../../types/api'

/**
 * Creates a trip through the progress stream and seeds the trip cache so the itinerary page renders without a
 * second request. `step` is the planning step the server reported last (null before the first event).
 */
export function useCreateTrip() {
  const queryClient = useQueryClient()
  const [step, setStep] = useState<string | null>(null)
  const mutation = useMutation<TripPlan, ApiError, TripRequest>({
    mutationFn: (request) => {
      setStep(null)
      return tripsApi.createWithProgress(request, setStep)
    },
    onSuccess: (plan) => queryClient.setQueryData(tripKeys.trip(plan.tripId), plan),
  })
  return { ...mutation, step }
}
