import type { ApiError } from '../api/client'

interface ErrorPanelProps {
  error: ApiError | Error
  onRetry?: () => void
  title?: string
}

/** User-facing error with the backend request id (for support) and an optional retry button. */
export function ErrorPanel({ error, onRetry, title }: ErrorPanelProps) {
  const requestId = 'requestId' in error ? error.requestId : undefined
  return (
    <div role="alert" className="rounded-tp bg-[#fdecea] p-5 text-[#7f1d1d]">
      {title && <p className="text-[17px] font-medium">{title}</p>}
      <p className="text-[16px]">{error.message}</p>
      {requestId && <p className="mt-1 text-[13px]">Mã yêu cầu: {requestId}</p>}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 min-h-10 rounded-tp bg-ink px-4 text-[15px] font-medium text-white transition-transform active:scale-[0.97]"
        >
          Thử lại
        </button>
      )}
    </div>
  )
}
