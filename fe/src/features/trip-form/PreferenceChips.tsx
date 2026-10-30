import { TAG_LABELS } from '../../lib/labels'
import { PREFERENCES, type Preference } from '../../types/api'
import { LIMITS } from './tripFormModel'

interface PreferenceChipsProps {
  selected: Preference[]
  onChange: (selected: Preference[]) => void
}

/** Toggle chips for the preferences the backend accepts; at most LIMITS.maxPreferences. */
export function PreferenceChips({ selected, onChange }: PreferenceChipsProps) {
  const full = selected.length >= LIMITS.maxPreferences
  const toggle = (p: Preference) =>
    onChange(selected.includes(p) ? selected.filter((s) => s !== p) : [...selected, p])

  return (
    <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Sở thích">
      {PREFERENCES.map((p) => {
        const on = selected.includes(p)
        return (
          <button
            key={p}
            type="button"
            aria-pressed={on}
            disabled={!on && full}
            onClick={() => toggle(p)}
            className={`min-h-10 rounded-tp px-3.5 text-[16px] transition-[background-color,transform] duration-150 active:scale-[0.97] disabled:cursor-not-allowed disabled:text-faint ${
              on ? 'bg-sun font-medium text-ink' : 'bg-sand text-ink hover:bg-stone'
            }`}
          >
            {TAG_LABELS[p] ?? p}
          </button>
        )
      })}
    </div>
  )
}
