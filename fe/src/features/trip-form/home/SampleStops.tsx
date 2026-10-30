import { Link } from 'react-router-dom'
import { formatDayHeading } from '../../../lib/format'
import { DEMO_PLAN } from '../../itinerary/demoPlan'
import { PlanItemCard } from '../../itinerary/PlanItemCard'

/**
 * The claim "every stop has a reason, a price and a source", proved by the product itself: day 1 of the sample
 * plan rendered by the same components as a real trip (an interface fragment, marked data-ui).
 */
export function SampleStops() {
  const day = DEMO_PLAN.days[0]
  return (
    <section
      aria-labelledby="sample-title"
      className="grid grid-cols-1 gap-x-[var(--gutter)] gap-y-12 px-[var(--margin)] py-[calc(var(--section)/2)] lg:grid-cols-12"
    >
      <div className="lg:col-span-5">
        <h2
          id="sample-title"
          data-reveal
          className="text-[length:var(--step-head)] leading-[1.02] font-bold tracking-[-0.035em] text-balance"
        >
          Mỗi điểm có lý do
        </h2>
        <p
          data-reveal
          style={{ ['--i' as string]: 1 }}
          className="mt-8 max-w-[44ch] text-[length:var(--step-body)] leading-[1.55]"
        >
          Lịch hiện giờ đi, giá vé kèm ngày cập nhật, lý do chọn và nguồn để bạn tự kiểm tra. Muốn
          đổi thì nhắn: “Ngày 2 trời mưa, đổi sang trong nhà”, chỉ ngày 2 thay đổi.
        </p>
        <Link
          to="/demo"
          data-reveal
          style={{ ['--i' as string]: 2 }}
          className="mt-8 inline-block text-[18px] font-medium underline decoration-sun decoration-2 underline-offset-4 hover:decoration-ink"
        >
          Xem trọn lịch mẫu Đà Nẵng 3 ngày
        </Link>
      </div>

      <div
        data-ui
        data-reveal
        style={{ ['--i' as string]: 2 }}
        className="rounded-tp bg-sand p-4 sm:p-6 lg:col-span-7"
      >
        <p className="mb-4 flex flex-wrap items-baseline justify-between gap-2 text-[18px] font-bold">
          Ngày 1 · {formatDayHeading(day.date)}
          <span className="text-[14px] font-normal text-muted">Đà Nẵng, lịch mẫu</span>
        </p>
        <ol className="overflow-hidden rounded-tp border border-line bg-white">
          {day.items.slice(0, 3).map((item) => (
            <PlanItemCard key={item.placeId} item={item} sources={DEMO_PLAN.sources} />
          ))}
        </ol>
      </div>
    </section>
  )
}
