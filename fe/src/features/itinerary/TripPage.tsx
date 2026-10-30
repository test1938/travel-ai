import { Link, useParams } from 'react-router-dom'
import { ErrorPanel } from '../../components/ErrorPanel'
import { TripView } from './TripView'
import { useTrip } from './useTrip'

/** /trips/:id: loads the saved plan, so a refresh shows the same itinerary. */
export function TripPage() {
  const { id = '' } = useParams()
  const trip = useTrip(id)

  if (trip.isPending) {
    return (
      <div className="mx-auto max-w-3xl space-y-3" aria-busy="true">
        <div className="h-8 w-2/3 animate-pulse rounded bg-slate-200" />
        <div className="h-24 animate-pulse rounded-xl bg-slate-200" />
        <div className="h-48 animate-pulse rounded-xl bg-slate-200" />
      </div>
    )
  }

  if (trip.isError) {
    return (
      <div className="mx-auto max-w-xl space-y-3">
        <ErrorPanel
          title={
            trip.error.code === 'NOT_FOUND'
              ? 'Không tìm thấy lịch trình này'
              : 'Không tải được lịch trình'
          }
          error={trip.error}
          onRetry={trip.error.code === 'NOT_FOUND' ? undefined : () => void trip.refetch()}
        />
        <Link to="/" className="inline-block text-sm text-sky-700 underline">
          Lập lịch mới
        </Link>
      </div>
    )
  }

  return <TripView plan={trip.data} />
}
