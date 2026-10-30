import { inputClass } from '../../components/Field'
import { LIMITS } from './tripFormModel'

interface ChildrenAgesInputProps {
  ages: string[]
  onChange: (ages: string[]) => void
  invalid: boolean
}

/** One age box per child, with add/remove buttons. */
export function ChildrenAgesInput({ ages, onChange, invalid }: ChildrenAgesInputProps) {
  const update = (index: number, value: string) =>
    onChange(ages.map((a, i) => (i === index ? value : a)))
  const remove = (index: number) => onChange(ages.filter((_, i) => i !== index))

  return (
    <div className="space-y-2">
      {ages.map((age, i) => (
        <div key={i} className="flex items-center gap-2">
          <label htmlFor={`child-${i}`} className="w-16 text-sm text-slate-600">
            Bé {i + 1}
          </label>
          <input
            id={`child-${i}`}
            type="number"
            inputMode="numeric"
            min={0}
            max={LIMITS.maxChildAge}
            value={age}
            onChange={(e) => update(i, e.target.value)}
            aria-invalid={
              invalid && (age === '' || Number(age) < 0 || Number(age) > LIMITS.maxChildAge)
            }
            className={`${inputClass} w-24`}
            placeholder="tuổi"
          />
          <span className="text-sm text-slate-500">tuổi</span>
          <button
            type="button"
            onClick={() => remove(i)}
            className="ml-auto text-sm text-slate-500 hover:text-red-600"
            aria-label={`Xóa bé ${i + 1}`}
          >
            Xóa
          </button>
        </div>
      ))}
      {ages.length < LIMITS.maxChildren && (
        <button
          type="button"
          onClick={() => onChange([...ages, ''])}
          className="text-sm font-medium text-sky-700"
        >
          + Thêm trẻ em
        </button>
      )}
    </div>
  )
}
