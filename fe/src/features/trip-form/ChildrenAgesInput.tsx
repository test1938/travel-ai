import { LIMITS } from './tripFormModel'

interface ChildrenAgesInputProps {
  ages: string[]
  onChange: (ages: string[]) => void
  invalid: boolean
  describedBy?: string
}

/** Children inside the sentence: ", bé [6] tuổi ×" per child, then "+ trẻ em" to add one. */
export function ChildrenAgesInput({
  ages,
  onChange,
  invalid,
  describedBy,
}: ChildrenAgesInputProps) {
  const update = (index: number, value: string) =>
    onChange(ages.map((a, i) => (i === index ? value : a)))
  const remove = (index: number) => onChange(ages.filter((_, i) => i !== index))
  const bad = (age: string) =>
    invalid && (age === '' || Number(age) < 0 || Number(age) > LIMITS.maxChildAge)

  return (
    <>
      {ages.map((age, i) => (
        <span key={i} className="whitespace-nowrap">
          , bé{' '}
          <input
            id={`child-${i}`}
            type="number"
            inputMode="numeric"
            min={0}
            max={LIMITS.maxChildAge}
            value={age}
            onChange={(e) => update(i, e.target.value)}
            aria-label={`Tuổi bé ${i + 1}`}
            aria-invalid={bad(age) || undefined}
            aria-describedby={describedBy}
            className="inline-field text-center"
            style={{ width: `${Math.max(age.length, 2) + 0.5}ch` }}
            placeholder="?"
          />{' '}
          tuổi
          <button
            type="button"
            onClick={() => remove(i)}
            aria-label={`Xóa bé ${i + 1}`}
            className="ml-1 inline-flex h-8 w-8 items-center justify-center rounded-tp align-middle text-[22px] text-muted transition-colors hover:bg-sand hover:text-ink"
          >
            ×
          </button>
        </span>
      ))}
      {ages.length < LIMITS.maxChildren && (
        <>
          {' '}
          <button
            type="button"
            onClick={() => onChange([...ages, ''])}
            className="rounded-tp bg-sand px-3 py-1 align-middle text-[18px] font-medium whitespace-nowrap transition-colors hover:bg-stone"
          >
            + {ages.length === 0 ? 'có trẻ em' : 'thêm bé'}
          </button>
        </>
      )}
    </>
  )
}
