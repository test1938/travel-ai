import { useNavigate } from 'react-router-dom'
import { ErrorPanel } from '../../components/ErrorPanel'
import type { TripRequest } from '../../types/api'
import { PlanningProgress } from './PlanningProgress'
import { TripForm } from './TripForm'
import { fromServerErrors } from './tripFormModel'
import { useCreateTrip } from './useCreateTrip'

/** Form on the left (top on mobile), progress or errors on the right; navigates to the trip when ready. */
export function HomePage() {
  const navigate = useNavigate()
  const createTrip = useCreateTrip()
  const fieldErrors =
    createTrip.error?.code === 'VALIDATION_FAILED'
      ? fromServerErrors(createTrip.error.fieldErrors)
      : undefined

  function submit(request: TripRequest) {
    createTrip.mutate(request, {
      onSuccess: (plan) => navigate(`/trips/${plan.tripId}`),
    })
  }

  return (
    <div className="mx-auto grid w-full max-w-5xl gap-6 md:grid-cols-[minmax(0,420px)_1fr]">
      <section className="rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="mb-4 text-lg font-semibold">Bạn muốn đi đâu?</h2>
        <TripForm onSubmit={submit} submitting={createTrip.isPending} serverErrors={fieldErrors} />
      </section>

      <section className="space-y-4">
        {createTrip.isPending && <PlanningProgress step={createTrip.step} />}
        {createTrip.isError && !fieldErrors && (
          <ErrorPanel
            title="Chưa lập được lịch trình"
            error={createTrip.error}
            onRetry={createTrip.variables ? () => submit(createTrip.variables) : undefined}
          />
        )}
        {createTrip.isIdle && (
          <div className="rounded-xl border border-dashed border-slate-300 p-6 text-sm text-slate-600">
            <p className="font-medium text-slate-800">TravelPilot lập lịch từng ngày cho bạn</p>
            <ul className="mt-2 list-disc space-y-1 pl-5">
              <li>Chỉ chọn địa điểm có trong dữ liệu đã kiểm tra, hợp tuổi của bé.</li>
              <li>Tổng chi phí do hệ thống tính, không để AI tự cộng.</li>
              <li>Mỗi gợi ý có lý do và nguồn tham khảo.</li>
            </ul>
          </div>
        )}
      </section>
    </div>
  )
}
