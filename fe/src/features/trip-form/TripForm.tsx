import { useState, type FormEvent } from 'react'
import { Field, inputClass } from '../../components/Field'
import { formatVnd, parseVnd } from '../../lib/format'
import { DESTINATION_LABELS, PACE_LABELS } from '../../lib/labels'
import type { Destination, Pace, TripRequest } from '../../types/api'
import { ChildrenAgesInput } from './ChildrenAgesInput'
import { PreferenceChips } from './PreferenceChips'
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
}

/** The trip request form; validates like the backend before sending. */
export function TripForm({ onSubmit, submitting, serverErrors }: TripFormProps) {
  const [values, setValues] = useState<TripFormValues>(() => defaultValues())
  const [errors, setErrors] = useState<TripFormErrors>({})
  const shown: TripFormErrors = { ...serverErrors, ...errors }

  function set<K extends keyof TripFormValues>(key: K, value: TripFormValues[K]) {
    setValues((v) => ({ ...v, [key]: value }))
    setErrors((e) => ({ ...e, [key]: undefined }))
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

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      <Field label="Điểm đến" htmlFor="destination" error={shown.destination}>
        <select
          id="destination"
          value={values.destination}
          onChange={(e) => set('destination', e.target.value as Destination)}
          className={inputClass}
        >
          {(Object.keys(DESTINATION_LABELS) as Destination[]).map((d) => (
            <option key={d} value={d}>
              {DESTINATION_LABELS[d]}
            </option>
          ))}
        </select>
      </Field>

      <div className="grid grid-cols-2 gap-3">
        <Field label="Ngày đi" htmlFor="startDate" error={shown.startDate}>
          <input
            id="startDate"
            type="date"
            min={todayInVietnam()}
            value={values.startDate}
            onChange={(e) => set('startDate', e.target.value)}
            aria-invalid={!!shown.startDate}
            className={inputClass}
          />
        </Field>
        <Field label="Số ngày" htmlFor="days" error={shown.days}>
          <select
            id="days"
            value={values.days}
            onChange={(e) => set('days', Number(e.target.value))}
            aria-invalid={!!shown.days}
            className={inputClass}
          >
            {Array.from({ length: LIMITS.maxDays }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>
                {n} ngày
              </option>
            ))}
          </select>
        </Field>
      </div>

      <Field label="Người lớn" htmlFor="adults" error={shown.adults}>
        <input
          id="adults"
          type="number"
          inputMode="numeric"
          min={LIMITS.minAdults}
          max={LIMITS.maxAdults}
          value={values.adults}
          onChange={(e) => set('adults', Number(e.target.value))}
          aria-invalid={!!shown.adults}
          className={`${inputClass} w-28`}
        />
      </Field>

      <Field
        label="Trẻ em"
        error={shown.childrenAges}
        hint="Tuổi giúp lọc chỗ không phù hợp với bé."
      >
        <ChildrenAgesInput
          ages={values.childrenAges}
          onChange={(ages) => set('childrenAges', ages)}
          invalid={!!shown.childrenAges}
        />
      </Field>

      <Field
        label="Ngân sách vui chơi và ăn uống"
        htmlFor="budget"
        error={shown.budget}
        hint={`Cho cả nhóm, chưa gồm khách sạn và di chuyển${Number.isNaN(budget) ? '' : ` · ${formatVnd(budget)}`}`}
      >
        <input
          id="budget"
          inputMode="numeric"
          value={values.budget}
          onChange={(e) => set('budget', e.target.value)}
          onBlur={() => !Number.isNaN(budget) && set('budget', formatVnd(budget).replace('đ', ''))}
          aria-invalid={!!shown.budget}
          className={inputClass}
          placeholder="6.000.000"
        />
      </Field>

      <Field
        label="Sở thích"
        error={shown.preferences}
        hint={`Chọn tối đa ${LIMITS.maxPreferences}.`}
      >
        <PreferenceChips selected={values.preferences} onChange={(p) => set('preferences', p)} />
      </Field>

      <Field label="Nhịp độ" error={shown.pace}>
        <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Nhịp độ">
          {(Object.keys(PACE_LABELS) as Pace[]).map((p) => (
            <button
              key={p}
              type="button"
              role="radio"
              aria-checked={values.pace === p}
              onClick={() => set('pace', p)}
              className={`rounded-lg border px-2 py-2 text-left text-sm ${
                values.pace === p ? 'border-sky-600 bg-sky-50' : 'border-slate-300 bg-white'
              }`}
            >
              <span className="block font-medium">{PACE_LABELS[p].label}</span>
              <span className="block text-xs text-slate-500">{PACE_LABELS[p].hint}</span>
            </button>
          ))}
        </div>
      </Field>

      <Field label="Ghi chú (không bắt buộc)" htmlFor="notes" error={shown.notes}>
        <textarea
          id="notes"
          rows={2}
          maxLength={LIMITS.maxNoteLength}
          value={values.notes}
          onChange={(e) => set('notes', e.target.value)}
          aria-invalid={!!shown.notes}
          className={inputClass}
          placeholder="Ví dụ: bé hay say xe, thích ăn hải sản"
        />
      </Field>

      {shown.form && <p className="text-sm text-red-600">{shown.form}</p>}

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-sky-600 px-4 py-3 font-medium text-white hover:bg-sky-700 disabled:cursor-wait disabled:opacity-60"
      >
        {submitting ? 'Đang lập lịch…' : 'Lập lịch trình'}
      </button>
    </form>
  )
}
