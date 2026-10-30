import { formatDate, formatVnd } from '../../lib/format'
import { DESTINATION_LABELS, TAG_LABELS } from '../../lib/labels'
import { DESTINATION_PHOTO } from '../../lib/photos'
import type { Destination, Pace } from '../../types/api'
import type { TripFormValues } from './tripFormModel'

/** Activities per day allowed by each pace (be: PlanValidator PACE rule). */
const PACE_MAX: Record<Pace, number> = { RELAXED: 2, NORMAL: 3, PACKED: 4 }

function addDays(iso: string, days: number): string | null {
  const date = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

interface TripPreviewProps {
  values: TripFormValues
  budget: number
  shortName: Record<Destination, string>
}

/**
 * The live answer to the sentence: the destination's picture and what the plan will respect, recomputed on
 * every change. A fragment of the product (data-ui), so it keeps the app's quiet rows.
 */
export function TripPreview({ values, budget, shortName }: TripPreviewProps) {
  const photo = DESTINATION_PHOTO[values.destination]
  const end = addDays(values.startDate, values.days - 1)
  const kids = values.childrenAges.filter((a) => a.trim() !== '')
  const perDay = Number.isNaN(budget) || budget <= 0 ? null : budget / values.days
  const maxStops = PACE_MAX[values.pace] * values.days

  const rows: [string, string][] = [
    [
      'Ngày',
      end
        ? `${formatDate(values.startDate).slice(0, 5)} – ${formatDate(end)} · ${values.days} ngày`
        : '—',
    ],
    [
      'Nhóm',
      `${values.adults} người lớn${kids.length ? `, ${kids.map((a) => `bé ${a} tuổi`).join(', ')}` : ''}`,
    ],
    ['Mỗi ngày', perDay ? `${formatVnd(Math.round(perDay / 1000) * 1000)} cho cả nhóm` : '—'],
    ['Hoạt động', `Tối đa ${maxStops} cả chuyến, ${PACE_MAX[values.pace]} mỗi ngày`],
    [
      'Sở thích',
      values.preferences.length
        ? values.preferences.map((p) => TAG_LABELS[p] ?? p).join(', ')
        : 'Chưa chọn',
    ],
  ]

  return (
    <section data-ui aria-labelledby="preview-title" className="rounded-tp bg-sand">
      <figure>
        <img
          key={photo.src}
          src={photo.src}
          width={photo.width}
          height={photo.height}
          alt={photo.alt}
          className="aspect-[16/10] w-full rounded-t-tp object-cover"
        />
        <figcaption className="px-6 pt-3 text-[14px] text-muted">{photo.place}</figcaption>
      </figure>
      <div className="px-6 pt-4 pb-6">
        <h3 id="preview-title" className="text-[22px] font-bold tracking-[-0.01em]">
          Lịch {shortName[values.destination]} {values.days} ngày
        </h3>
        <p className="mt-1 text-[15px] text-muted">{DESTINATION_LABELS[values.destination]}</p>
        <dl className="mt-4 text-[16px]">
          {rows.map(([label, value]) => (
            <div
              key={label}
              className="grid grid-cols-[96px_1fr] gap-3 border-t border-line py-2.5 first:border-t-0"
            >
              <dt className="text-muted">{label}</dt>
              <dd className="tabular-nums">{value}</dd>
            </div>
          ))}
        </dl>
        {kids.length > 0 && (
          <p className="mt-3 text-[15px]">
            Chỗ không nhận bé {Math.min(...kids.map(Number))} tuổi sẽ bị loại trước khi AI chọn.
          </p>
        )}
      </div>
    </section>
  )
}
