import { useState, type FormEvent, type ReactNode } from 'react'
import { parseVnd, formatVnd } from '../../lib/format'
import { PACE_LABELS } from '../../lib/labels'
import type { Destination, Pace, TripRequest } from '../../types/api'
import { ChildrenAgesInput } from './ChildrenAgesInput'
import { PreferenceChips } from './PreferenceChips'
import { TripPreview } from './TripPreview'
import {
  defaultValues,
  LIMITS,
  todayInVietnam,
  toRequest,
  validate,
  type TripFormErrors,
  type TripFormValues,
} from './tripFormModel'

interface TripFormProps {
  onSubmit: (request: TripRequest) => void
  submitting: boolean
  /** Field errors returned by the backend (already mapped to form fields). */
  serverErrors?: TripFormErrors
  /** Replaces the live preview while the plan is being made or after an error. */
  status?: ReactNode
}

/** Short names for the sentence; the full label (with Hội An and Bà Nà) is in the preview. */
const SHORT_DESTINATION: Record<Destination, string> = {
  DANANG: 'Đà Nẵng',
  HANOI: 'Hà Nội',
  QUANGNINH: 'Hạ Long',
}

/** Three real requests that refill the sentence: the demo scenarios of plan/manual-test-v0.1.md. */
const PRESETS: { label: string; values: Partial<TripFormValues> }[] = [
  {
    label: 'Gia đình có bé 6 tuổi, Đà Nẵng 3 ngày',
    values: {
      destination: 'DANANG',
      days: 3,
      adults: 2,
      childrenAges: ['6'],
      budget: '6.000.000',
      preferences: ['BEACH', 'THEME-PARK'],
      pace: 'RELAXED',
    },
  },
  {
    label: 'Hai người, Hà Nội 2 ngày ăn vặt',
    values: {
      destination: 'HANOI',
      days: 2,
      adults: 2,
      childrenAges: [],
      budget: '3.000.000',
      preferences: ['STREET-FOOD', 'PHOTO', 'CULTURE'],
      pace: 'RELAXED',
    },
  },
  {
    label: 'Bốn bạn, Hạ Long 2 ngày',
    values: {
      destination: 'QUANGNINH',
      days: 2,
      adults: 4,
      childrenAges: [],
      budget: '8.000.000',
      preferences: ['NATURE', 'SEAFOOD'],
      pace: 'NORMAL',
    },
  },
]

const ERROR_ORDER: (keyof TripFormErrors)[] = [
  'destination',
  'days',
  'startDate',
  'adults',
  'childrenAges',
  'budget',
  'preferences',
  'pace',
  'notes',
  'form',
]

/** Width in ch for an inline field, so the sentence closes up around its value. */
const fit = (text: string, min: number) => `${Math.max(text.length, min) + 0.5}ch`

/**
 * The trip request written as one sentence ("Đi Đà Nẵng 3 ngày, từ…, 2 người lớn…"): every blank is a real
 * control, and the preview beside it answers as the sentence changes. Validates like the backend before sending.
 */
export function TripForm({ onSubmit, submitting, serverErrors, status }: TripFormProps) {
  const [values, setValues] = useState<TripFormValues>(() => defaultValues())
  const [errors, setErrors] = useState<TripFormErrors>({})
  const shown: TripFormErrors = { ...serverErrors, ...errors }
  const errorList = ERROR_ORDER.filter((k) => shown[k])

  function set<K extends keyof TripFormValues>(key: K, value: TripFormValues[K]) {
    setValues((v) => ({ ...v, [key]: value }))
    setErrors((e) => ({ ...e, [key]: undefined }))
  }

  function applyPreset(preset: Partial<TripFormValues>) {
    setValues((v) => ({ ...v, ...preset }))
    setErrors({})
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found = validate(values)
    setErrors(found)
    if (Object.keys(found).length === 0 && !submitting) {
      onSubmit(toRequest(values))
    }
  }

  const budget = parseVnd(values.budget)
  const invalid = (key: keyof TripFormErrors) => (shown[key] ? true : undefined)
  const describedBy = (key: keyof TripFormErrors) => (shown[key] ? `err-${key}` : undefined)

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="grid grid-cols-1 gap-x-[var(--gutter)] lg:grid-cols-12"
    >
      <h2
        id="plan-title"
        data-reveal
        className="text-[length:var(--step-head)] leading-[1.02] font-bold tracking-[-0.035em] text-balance lg:col-span-12"
      >
        Đi{' '}
        <span className="inline-select">
          <select
            id="destination"
            aria-label="Điểm đến"
            value={values.destination}
            onChange={(e) => set('destination', e.target.value as Destination)}
            aria-invalid={invalid('destination')}
            aria-describedby={describedBy('destination')}
            className="inline-field"
          >
            {(Object.keys(SHORT_DESTINATION) as Destination[]).map((d) => (
              <option key={d} value={d}>
                {SHORT_DESTINATION[d]}
              </option>
            ))}
          </select>
        </span>{' '}
        <span className="inline-select">
          <select
            id="days"
            aria-label="Số ngày"
            value={values.days}
            onChange={(e) => set('days', Number(e.target.value))}
            aria-invalid={invalid('days')}
            aria-describedby={describedBy('days')}
            className="inline-field"
          >
            {Array.from({ length: LIMITS.maxDays }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>
                {n} ngày
              </option>
            ))}
          </select>
        </span>
      </h2>

      <div className="mt-10 lg:col-span-7 lg:mt-12">
        <p
          data-reveal
          style={{ ['--i' as string]: 1 }}
          className="max-w-none text-[length:var(--step-statement)] leading-[1.5] font-normal"
        >
          Khởi hành{' '}
          <input
            id="startDate"
            type="date"
            aria-label="Ngày đi"
            min={todayInVietnam()}
            value={values.startDate}
            onChange={(e) => set('startDate', e.target.value)}
            aria-invalid={invalid('startDate')}
            aria-describedby={describedBy('startDate')}
            className="inline-field"
          />
          , nhóm có{' '}
          <input
            id="adults"
            type="number"
            inputMode="numeric"
            aria-label="Số người lớn"
            min={LIMITS.minAdults}
            max={LIMITS.maxAdults}
            value={values.adults}
            onChange={(e) => set('adults', Number(e.target.value))}
            aria-invalid={invalid('adults')}
            aria-describedby={describedBy('adults')}
            className="inline-field text-center"
            style={{ width: fit(String(values.adults), 1) }}
          />{' '}
          người lớn
          <ChildrenAgesInput
            ages={values.childrenAges}
            onChange={(ages) => set('childrenAges', ages)}
            invalid={!!shown.childrenAges}
            describedBy={describedBy('childrenAges')}
          />
          .
        </p>
        <p
          data-reveal
          style={{ ['--i' as string]: 2 }}
          className="mt-4 max-w-none text-[length:var(--step-statement)] leading-[1.5]"
        >
          <label htmlFor="budget">Ngân sách vui chơi và ăn uống</label>{' '}
          <input
            id="budget"
            inputMode="numeric"
            value={values.budget}
            onChange={(e) => set('budget', e.target.value)}
            onBlur={() =>
              !Number.isNaN(budget) && set('budget', formatVnd(budget).replace('đ', ''))
            }
            aria-invalid={invalid('budget')}
            aria-describedby={describedBy('budget')}
            className="inline-field text-right"
            style={{ width: fit(values.budget, 6) }}
            placeholder="6.000.000"
          />
          đ cho cả nhóm, chưa gồm khách sạn và đi lại.
        </p>

        {errorList.length > 0 && (
          <ul role="alert" className="mt-6 space-y-1 text-[16px] text-red-700">
            {errorList.map((k) => (
              <li key={k} id={`err-${k}`}>
                {shown[k]}
              </li>
            ))}
          </ul>
        )}

        <div data-reveal style={{ ['--i' as string]: 3 }} className="mt-8">
          <p className="text-[18px] text-muted">Hoặc bắt đầu từ một chuyến có sẵn:</p>
          <div className="mt-2 flex flex-wrap gap-x-6 gap-y-2">
            {PRESETS.map((p) => (
              <button
                key={p.label}
                type="button"
                onClick={() => applyPreset(p.values)}
                className="text-left text-[18px] font-medium underline decoration-sun decoration-2 underline-offset-4 transition-colors hover:decoration-ink"
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>

        <fieldset className="mt-12" aria-describedby={describedBy('preferences')}>
          <legend className="text-[18px] font-medium">
            Sở thích, chọn tối đa {LIMITS.maxPreferences}
          </legend>
          <PreferenceChips selected={values.preferences} onChange={(p) => set('preferences', p)} />
        </fieldset>

        <fieldset className="mt-10">
          <legend className="text-[18px] font-medium">Nhịp độ</legend>
          <div className="mt-3 grid grid-cols-3 gap-2" role="radiogroup" aria-label="Nhịp độ">
            {(Object.keys(PACE_LABELS) as Pace[]).map((p) => {
              const on = values.pace === p
              return (
                <button
                  key={p}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => set('pace', p)}
                  className={`rounded-tp px-3 py-3 text-left transition-colors ${
                    on ? 'bg-ink text-white' : 'bg-sand text-ink hover:bg-stone'
                  }`}
                >
                  <span className="block text-[16px] font-medium">{PACE_LABELS[p].label}</span>
                  <span className={`block text-[14px] ${on ? 'text-white/85' : 'text-muted'}`}>
                    {PACE_LABELS[p].hint}
                  </span>
                </button>
              )
            })}
          </div>
        </fieldset>

        <div className="mt-10">
          <label htmlFor="notes" className="block text-[18px] font-medium">
            Ghi chú, nếu có
          </label>
          <textarea
            id="notes"
            rows={2}
            maxLength={LIMITS.maxNoteLength}
            value={values.notes}
            onChange={(e) => set('notes', e.target.value)}
            aria-invalid={invalid('notes')}
            aria-describedby={describedBy('notes')}
            className="mt-3 w-full resize-y rounded-tp bg-sand px-4 py-3 text-[18px] placeholder:text-faint focus:bg-white focus:outline-2 focus:outline-ink"
            placeholder="Ví dụ: bé hay say xe, thích ăn hải sản"
          />
        </div>

        <div className="mt-10 flex flex-wrap items-center gap-x-6 gap-y-3">
          <button type="submit" disabled={submitting} className="btn-sun min-h-14 px-8 text-[18px]">
            {submitting ? 'Đang lập lịch…' : 'Lập lịch trình'}
          </button>
          <p className="text-[14px] text-muted">Thường xong trong 10–20 giây.</p>
        </div>
      </div>

      <div className="mt-14 lg:col-span-5 lg:mt-12">
        <div className="lg:sticky lg:top-6">
          {status ?? <TripPreview values={values} budget={budget} shortName={SHORT_DESTINATION} />}
        </div>
      </div>
    </form>
  )
}
