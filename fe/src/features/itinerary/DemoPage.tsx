import { DEMO_PLAN } from './demoPlan'
import { TripView } from './TripView'

/** /demo: the itinerary UI with sample data, no backend needed. */
export function DemoPage() {
  return (
    <TripView
      plan={DEMO_PLAN}
      header={
        <p className="rounded-tp border border-[#fde68a] bg-[#fffbeb] px-3 py-2 text-[14px] text-[#92400e]">
          Lịch mẫu để xem giao diện, không phải kết quả AI vừa lập.
        </p>
      }
    />
  )
}
