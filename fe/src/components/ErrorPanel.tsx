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
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
      {title && <p className="font-medium">{title}</p>}
      <p>{error.message}</p>
      {requestId && <p className="mt-1 text-xs text-red-700">Mã yêu cầu: {requestId}</p>}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white"
        >
          Thử lại
        </button>
      )}
    </div>
  )
}
