import type { PlanItem } from '../types/api'

const vndFormatter = new Intl.NumberFormat('vi-VN')

/** 1500000 -> "1.500.000đ" */
export function formatVnd(amount: number): string {
  return `${vndFormatter.format(Math.round(amount))}đ`
}

/** Price label for an itinerary item: "Miễn phí", "~208.000đ" (estimate) or "2.800.000đ" (checked). */
export function priceLabel(item: Pick<PlanItem, 'costVnd' | 'costEstimated'>): string {
  if (item.costVnd === 0) {
    return item.costEstimated ? 'Miễn phí (ước tính)' : 'Miễn phí'
  }
  return item.costEstimated ? `~${formatVnd(item.costVnd)}` : formatVnd(item.costVnd)
}

/** "2026-10-12" -> "12/10/2026"; returns the input when it is not an ISO date. */
export function formatDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  return m ? `${m[3]}/${m[2]}/${m[1]}` : iso
}

/** "2026-11-12" -> "Thứ Năm, 12/11" */
export function formatDayHeading(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  const weekday = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'][
    date.getDay()
  ]
  return `${weekday}, ${formatDate(iso).slice(0, 5)}`
}

/** Days between a verifyAt date and today; null when unknown. */
export function daysSince(iso: string | null, today: Date = new Date()): number | null {
  if (!iso) {
    return null
  }
  const then = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(then.getTime())) {
    return null
  }
  return Math.floor((today.getTime() - then.getTime()) / 86_400_000)
}

/** Share of the budget used, 0..100+ (can exceed 100 when over budget). */
export function budgetPercent(total: number, limit: number): number {
  if (limit <= 0) {
    return 0
  }
  return Math.round((total / limit) * 100)
}

/** Parses a VND input like "6.000.000" or "6000000đ" into a number; NaN when empty. */
export function parseVnd(text: string): number {
  const digits = text.replace(/[^\d]/g, '')
  return digits === '' ? Number.NaN : Number(digits)
}

/** "08:30" / "2h 30p" style duration */
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) {
    return `${m} phút`
  }
  return m === 0 ? `${h} giờ` : `${h} giờ ${m} phút`
}
