import { useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { ErrorPanel } from '../../components/ErrorPanel'
import { useReveal } from '../../lib/reveal'
import type { TripRequest } from '../../types/api'
import { HeroReel } from './home/HeroReel'
import { HowItWorks } from './home/HowItWorks'
import { PlacesSentence } from './home/PlacesSentence'
import { SampleStops } from './home/SampleStops'
import { SiteFooter } from './home/SiteFooter'
import { PhotoBand } from './home/PhotoBand'
import { PlanningProgress } from './PlanningProgress'
import { TripForm } from './TripForm'
import { fromServerErrors } from './tripFormModel'
import { useCreateTrip } from './useCreateTrip'

/**
 * The home page, read as a series of sentences with their evidence: the title split by a reel of the three
 * destinations, the trip request written as one sentence, the planning steps at their real length, one stop as
 * the product shows it, the places it knows, and the invitation to plan. Navigates to the trip when ready.
 */
export function HomePage() {
  const navigate = useNavigate()
  const createTrip = useCreateTrip()
  const page = useRef<HTMLDivElement>(null)
  useReveal(page)
  const fieldErrors =
    createTrip.error?.code === 'VALIDATION_FAILED'
      ? fromServerErrors(createTrip.error.fieldErrors)
      : undefined

  function submit(request: TripRequest) {
    createTrip.mutate(request, {
      onSuccess: (plan) => navigate(`/trips/${plan.tripId}`),
    })
  }

  const status = createTrip.isPending ? (
    <PlanningProgress step={createTrip.step} />
  ) : createTrip.isError && !fieldErrors ? (
    <ErrorPanel
      title="Chưa lập được lịch trình"
      error={createTrip.error}
      onRetry={createTrip.variables ? () => submit(createTrip.variables) : undefined}
    />
  ) : undefined

  return (
    <div ref={page}>
      <HeroReel />
      <section
        id="plan"
        aria-labelledby="plan-title"
        className="scroll-mt-4 px-[var(--margin)] pt-[calc(var(--section)/2)] pb-[calc(var(--section)/2)]"
      >
        <TripForm
          onSubmit={submit}
          submitting={createTrip.isPending}
          serverErrors={fieldErrors}
          status={status}
        />
      </section>
      <HowItWorks />
      <PhotoBand />
      <SampleStops />
      <PlacesSentence />
      <SiteFooter />
    </div>
  )
}
