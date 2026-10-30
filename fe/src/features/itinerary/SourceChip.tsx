import { useEffect, useId, useRef, useState } from 'react'
import type { Source } from '../../types/api'
import { safeUrl } from './itineraryModel'

/** Small citation chip; click opens a popover with the passage snippet, link and licence. */
export function SourceChip({ source }: { source: Source }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLSpanElement>(null)
  const panelId = useId()
  const url = safeUrl(source.url)

  useEffect(() => {
    if (!open) {
      return
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onClick)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onClick)
    }
  }, [open])

  return (
    <span ref={ref} className="relative inline-block">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((o) => !o)}
        className="rounded-full border border-slate-300 bg-slate-50 px-2 py-0.5 text-xs text-slate-600 hover:border-sky-400"
      >
        Nguồn {source.id.replace('ev-', '#')}
      </button>
      {open && (
        <span
          id={panelId}
          role="dialog"
          aria-label={`Nguồn: ${source.title}`}
          className="absolute left-0 z-20 mt-1 block w-72 rounded-lg border border-slate-200 bg-white p-3 text-left text-xs shadow-lg"
        >
          <span className="block font-medium text-slate-800">{source.title}</span>
          {/* Plain text only: passages come from external data. */}
          <span className="mt-1 block whitespace-pre-line text-slate-600">{source.snippet}</span>
          {url && (
            <a
              href={url}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-2 block break-all text-sky-700 underline"
            >
              {url}
            </a>
          )}
          {source.license && (
            <span className="mt-1 block text-slate-400">Giấy phép: {source.license}</span>
          )}
        </span>
      )}
    </span>
  )
}
