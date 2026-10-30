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
    <div className="flex flex-wrap gap-2" role="group" aria-label="Sở thích">
      {PREFERENCES.map((p) => {
        const on = selected.includes(p)
        return (
          <button
            key={p}
            type="button"
            aria-pressed={on}
            disabled={!on && full}
            onClick={() => toggle(p)}
            className={`rounded-full border px-3 py-1 text-sm transition disabled:opacity-40 ${
              on
                ? 'border-sky-600 bg-sky-600 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:border-sky-400'
            }`}
          >
            {TAG_LABELS[p] ?? p}
          </button>
        )
      })}
    </div>
  )
}
