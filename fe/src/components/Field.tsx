import type { ReactNode } from 'react'

interface FieldProps {
  label: string
  htmlFor?: string
  hint?: string
  error?: string
  children: ReactNode
}

/** Label + control + hint/error, with the error linked for screen readers. */
export function Field({ label, htmlFor, hint, error, children }: FieldProps) {
  const describedBy = htmlFor ? `${htmlFor}-msg` : undefined
  return (
    <div className="space-y-1">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {(error || hint) && (
        <p id={describedBy} className={`text-xs ${error ? 'text-red-600' : 'text-slate-500'}`}>
          {error ?? hint}
        </p>
      )}
    </div>
  )
}

export const inputClass =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-sky-500 focus:outline-none aria-[invalid=true]:border-red-400'
