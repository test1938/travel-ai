import { DEMO_PLAN } from './demoPlan'
import { TripView } from './TripView'

/** /demo: the itinerary UI with sample data, no backend needed. */
export function DemoPage() {
  return (
    <div className="space-y-4">
      <p className="mx-auto max-w-5xl rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
        Đây là lịch mẫu để xem giao diện, không phải kết quả từ AI.
      </p>
      <TripView plan={DEMO_PLAN} />
    </div>
  )
}
