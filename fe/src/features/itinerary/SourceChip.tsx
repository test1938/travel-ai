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
        className="rounded-[2px] border border-control bg-white px-1.5 py-px text-[12px] text-muted transition-colors hover:border-ink hover:text-ink aria-expanded:border-ink aria-expanded:text-ink"
      >
        Nguồn {source.id.replace('ev-', '#')}
      </button>
      {open && (
        <span
          id={panelId}
          role="dialog"
          aria-label={`Nguồn: ${source.title}`}
          // taste-skill-ignore: a popover floats over the stops, so it carries the one shadow
          className="absolute left-0 z-20 mt-1 block w-72 max-w-[80vw] rounded-tp border border-line bg-white p-3 text-left text-[13px] shadow-[0_8px_24px_rgba(0,0,0,0.12)]"
        >
          <span className="block font-medium text-ink">{source.title}</span>
          {/* Plain text only: passages come from external data. */}
          <span className="mt-1 block whitespace-pre-line text-muted">{source.snippet}</span>
          {url && (
            <a
              href={url}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-2 block break-all text-ink underline"
            >
              {url}
            </a>
          )}
          {source.license && (
            <span className="mt-1 block text-faint">Giấy phép: {source.license}</span>
          )}
        </span>
      )}
    </span>
  )
}
