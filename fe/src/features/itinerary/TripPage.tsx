import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import { tripKeys, tripsApi } from '../../api/trips'
import { ErrorPanel } from '../../components/ErrorPanel'
import type { ChatReply, TripPlan } from '../../types/api'
import { ChatPanel } from '../chat/ChatPanel'
import { changedItems } from '../chat/planDiff'
import { TripView } from './TripView'
import { useTrip } from './useTrip'

const HIGHLIGHT_MS = 3000

/** /trips/:id: loads the saved plan (refresh-safe), chat to change it, switch between versions. */
export function TripPage() {
  const { id = '' } = useParams()
  const trip = useTrip(id)
  const [viewVersion, setViewVersion] = useState<number | null>(null)
  const [highlight, setHighlight] = useState<Set<string>>(new Set())
  const [banner, setBanner] = useState<string | null>(null)
  const lastPlan = useRef<TripPlan | null>(null)

  const current = trip.data?.version
  const showingOld = viewVersion != null && current != null && viewVersion !== current
  const oldVersion = useQuery<TripPlan, ApiError>({
    queryKey: tripKeys.version(id, viewVersion ?? 0),
    queryFn: () => tripsApi.version(id, viewVersion ?? 0),
    enabled: showingOld,
    staleTime: Infinity,
  })

  useEffect(() => {
    if (trip.data) {
      lastPlan.current = trip.data
    }
  }, [trip.data])

  useEffect(() => {
    if (highlight.size === 0) {
      return
    }
    const timer = setTimeout(() => setHighlight(new Set()), HIGHLIGHT_MS)
    return () => clearTimeout(timer)
  }, [highlight])

  function onReply(reply: ChatReply) {
    if (reply.plan && lastPlan.current) {
      setHighlight(changedItems(lastPlan.current, reply.plan))
      setBanner(`v${reply.plan.version}: ${reply.changeSummary ?? 'lịch đã được cập nhật'}`)
      setViewVersion(null)
    }
  }

  if (trip.isPending) {
    return (
      <div
        className="mx-auto grid max-w-[1152px] gap-6 lg:grid-cols-[minmax(0,1fr)_384px]"
        aria-busy="true"
      >
        <div className="space-y-4">
          <div className="h-8 w-2/3 rounded-tp bg-stone" />
          <div className="h-4 w-1/3 rounded-tp bg-stone" />
          <div className="h-72 rounded-tp border border-line bg-white" />
        </div>
        <div className="h-56 rounded-tp border border-line bg-white" />
      </div>
    )
  }

  if (trip.isError) {
    return (
      <div className="mx-auto max-w-xl space-y-4">
        <ErrorPanel
          title={
            trip.error.code === 'NOT_FOUND'
              ? 'Không tìm thấy lịch trình này'
              : 'Không tải được lịch trình'
          }
          error={trip.error}
          onRetry={trip.error.code === 'NOT_FOUND' ? undefined : () => void trip.refetch()}
        />
        <Link to="/" className="inline-block text-[15px] underline">
          Lập lịch mới
        </Link>
      </div>
    )
  }

  const latest = trip.data
  const plan = showingOld && oldVersion.data ? oldVersion.data : latest
  const header = (
    <div className="space-y-2">
      {latest.version > 1 && (
        <div className="flex flex-wrap items-center gap-3 text-[14px]">
          <span className="text-muted">Phiên bản</span>
          <div
            className="inline-flex rounded-tp bg-stone p-0.5"
            role="group"
            aria-label="Phiên bản"
          >
            {Array.from({ length: latest.version }, (_, i) => i + 1).map((v) => {
              const active = (viewVersion ?? latest.version) === v
              return (
                <button
                  key={v}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setViewVersion(v === latest.version ? null : v)}
                  className={`min-h-8 min-w-10 rounded-[2px] px-2.5 tabular-nums transition-colors ${
                    active ? 'bg-white font-medium text-ink' : 'text-muted hover:text-ink'
                  }`}
                >
                  v{v}
                </button>
              )
            })}
          </div>
        </div>
      )}
      {showingOld && (
        <p className="rounded-tp border border-line bg-white p-3 text-[14px]">
          Đang xem phiên bản cũ v{viewVersion} (chỉ đọc).{' '}
          <button
            type="button"
            className="font-medium underline"
            onClick={() => setViewVersion(null)}
          >
            Về phiên bản mới nhất
          </button>
          {oldVersion.isPending && ' Đang tải…'}
        </p>
      )}
      {showingOld && oldVersion.isError && <ErrorPanel error={oldVersion.error} />}
      {banner && !showingOld && (
        <p role="status" className="rounded-tp bg-sun-pale p-3 text-[14px] text-ink">
          {banner}
        </p>
      )}
    </div>
  )

  return (
    <TripView
      plan={plan}
      highlight={showingOld ? undefined : highlight}
      header={header}
      aside={<ChatPanel tripId={id} onReply={onReply} disabled={showingOld} />}
    />
  )
}
