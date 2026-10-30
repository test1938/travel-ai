import { useState, type FormEvent } from 'react'
import { useAiChat } from './useAiChat'

const MAX_LENGTH = 2000

/** Walking skeleton (T0.10): type a question, get the model's answer and latency. */
export function ChatPlayground() {
  const [message, setMessage] = useState('')
  const chat = useAiChat()
  const trimmed = message.trim()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (trimmed && !chat.isPending) {
      chat.mutate({ message: trimmed })
    }
  }

  return (
    <section className="mx-auto w-full max-w-2xl space-y-4">
      <form onSubmit={handleSubmit} className="space-y-3">
        <label htmlFor="message" className="block text-sm font-medium">
          Hỏi TravelPilot
        </label>
        <textarea
          id="message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          maxLength={MAX_LENGTH}
          rows={4}
          placeholder="Ví dụ: Gia đình có bé 6 tuổi, 3 ngày ở Đà Nẵng nên đi đâu?"
          className="w-full rounded-tp border border-control bg-white p-3 focus:border-ink focus:outline-none"
        />
        <div className="flex items-center justify-between text-sm text-muted">
          <span>
            {message.length}/{MAX_LENGTH}
          </span>
          <button
            type="submit"
            disabled={!trimmed || chat.isPending}
            className="rounded-tp bg-ink px-4 py-2 font-medium text-white disabled:cursor-not-allowed disabled:bg-stone disabled:text-muted"
          >
            {chat.isPending ? 'Đang hỏi…' : 'Gửi'}
          </button>
        </div>
      </form>

      {chat.isPending && <p className="text-muted">AI đang trả lời…</p>}

      {chat.isError && (
        <div role="alert" className="rounded-tp bg-[#fdecea] p-3 text-[#7f1d1d]">
          <p>{chat.error.message}</p>
          {chat.error.requestId && (
            <p className="mt-1 text-xs">Mã yêu cầu: {chat.error.requestId}</p>
          )}
          <button
            type="button"
            onClick={() => chat.mutate({ message: trimmed })}
            className="mt-2 text-sm font-medium underline"
          >
            Thử lại
          </button>
        </div>
      )}

      {chat.isSuccess && (
        <article className="rounded-tp border border-line bg-white p-4">
          <p className="whitespace-pre-wrap">{chat.data.answer}</p>
          <p className="mt-3 text-xs text-muted">
            {chat.data.model ?? 'model'} · {(chat.data.latencyMs / 1000).toFixed(1)} giây
          </p>
        </article>
      )}
    </section>
  )
}
