import { parseVnd } from '../../lib/format'
import type { Destination, Pace, Preference, TripRequest } from '../../types/api'
import { PREFERENCES } from '../../types/api'

/** Form state: text fields stay strings so partial input can be shown and validated. */
export interface TripFormValues {
  destination: Destination
  startDate: string
  days: number
  adults: number
  childrenAges: string[]
  budget: string
  preferences: Preference[]
  pace: Pace
  notes: string
}

export type TripFormErrors = Partial<Record<keyof TripFormValues | 'form', string>>

/** Same limits as the backend (be: TripRequest + TripRequestValidator). */
export const LIMITS = {
  minDays: 1,
  maxDays: 5,
  minAdults: 1,
  maxAdults: 10,
  maxChildren: 6,
  maxChildAge: 17,
  maxPreferences: 6,
  maxBudget: 1_000_000_000,
  maxNoteLength: 200,
} as const

/** Today in Vietnam time as yyyy-MM-dd (the backend checks "startDate >= today" in Asia/Ho_Chi_Minh). */
export function todayInVietnam(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(now)
}

export function defaultValues(now: Date = new Date()): TripFormValues {
  const start = new Date(`${todayInVietnam(now)}T00:00:00`)
  start.setDate(start.getDate() + 14)
  const iso = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, '0')}-${String(start.getDate()).padStart(2, '0')}`
  return {
    destination: 'DANANG',
    startDate: iso,
    days: 3,
    adults: 2,
    childrenAges: [],
    budget: '6.000.000',
    preferences: [],
    pace: 'RELAXED',
    notes: '',
  }
}

/** Client-side validation mirroring the backend; returns an empty object when valid. */
export function validate(values: TripFormValues, now: Date = new Date()): TripFormErrors {
  const errors: TripFormErrors = {}
  if (!/^\d{4}-\d{2}-\d{2}$/.test(values.startDate)) {
    errors.startDate = 'Chọn ngày đi.'
  } else if (values.startDate < todayInVietnam(now)) {
    errors.startDate = 'Ngày đi phải từ hôm nay trở đi.'
  }
  if (
    !Number.isInteger(values.days) ||
    values.days < LIMITS.minDays ||
    values.days > LIMITS.maxDays
  ) {
    errors.days = `Số ngày từ ${LIMITS.minDays} đến ${LIMITS.maxDays}.`
  }
  if (
    !Number.isInteger(values.adults) ||
    values.adults < LIMITS.minAdults ||
    values.adults > LIMITS.maxAdults
  ) {
    errors.adults = `Số người lớn từ ${LIMITS.minAdults} đến ${LIMITS.maxAdults}.`
  }
  if (values.childrenAges.length > LIMITS.maxChildren) {
    errors.childrenAges = `Tối đa ${LIMITS.maxChildren} trẻ em.`
  } else if (
    values.childrenAges.some((a) => {
      const n = Number(a)
      return a.trim() === '' || !Number.isInteger(n) || n < 0 || n > LIMITS.maxChildAge
    })
  ) {
    errors.childrenAges = `Nhập tuổi từng bé (0–${LIMITS.maxChildAge}).`
  }
  const budget = parseVnd(values.budget)
  if (Number.isNaN(budget) || budget <= 0) {
    errors.budget = 'Nhập ngân sách lớn hơn 0.'
  } else if (budget > LIMITS.maxBudget) {
    errors.budget = 'Ngân sách quá lớn.'
  }
  if (values.preferences.length > LIMITS.maxPreferences) {
    errors.preferences = `Chọn tối đa ${LIMITS.maxPreferences} sở thích.`
  } else if (values.preferences.some((p) => !(PREFERENCES as readonly string[]).includes(p))) {
    errors.preferences = 'Sở thích không hợp lệ.'
  }
  if (values.notes.length > LIMITS.maxNoteLength) {
    errors.notes = `Ghi chú tối đa ${LIMITS.maxNoteLength} ký tự.`
  }
  return errors
}

/** Converts valid form values into the API request. */
export function toRequest(values: TripFormValues): TripRequest {
  const note = values.notes.trim()
  return {
    destination: values.destination,
    startDate: values.startDate,
    days: values.days,
    adults: values.adults,
    childrenAges: values.childrenAges.map(Number),
    budgetVnd: parseVnd(values.budget),
    preferences: values.preferences,
    pace: values.pace,
    notes: note ? [note] : [],
  }
}

/** Maps backend field errors (TripRequest field names) onto form fields. */
export function fromServerErrors(errors: Record<string, string> | undefined): TripFormErrors {
  if (!errors) {
    return {}
  }
  const mapped: TripFormErrors = {}
  for (const [field, message] of Object.entries(errors)) {
    const key = field.replace(/\[.*$/, '')
    const target: keyof TripFormValues | 'form' =
      key === 'budgetVnd'
        ? 'budget'
        : key in defaultValues()
          ? (key as keyof TripFormValues)
          : 'form'
    mapped[target] ??= message
  }
  return mapped
}
