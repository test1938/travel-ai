import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { ErrorPanel } from '../../components/ErrorPanel'
import type { ChatReply, Intent, Source } from '../../types/api'
import { SourceChip } from '../itinerary/SourceChip'
import { useChatHistory, useSendMessage } from './useChat'

const MAX_LENGTH = 1000

const EXAMPLES = [
  'Ngày 2 trời mưa, đổi sang hoạt động trong nhà',
  'Giảm ngân sách còn 3 triệu',
  'Chỗ nào trong lịch hợp với bé nhất?',
]

const INTENT_LABELS: Record<Intent, string> = {
  ASK_QUESTION: 'Trả lời',
  MODIFY_PLAN: 'Đổi lịch',
  SMALL_TALK: 'Trò chuyện',
  OUT_OF_SCOPE: 'Ngoài phạm vi',
}

interface ChatPanelProps {
  tripId: string
  /** Called after each reply (the trip page highlights changes when a new version arrives). */
  onReply: (reply: ChatReply) => void
  disabled?: boolean
}

/** Chat on a trip: ask questions or change the plan. Citations are shown for answers given in this session. */
export function ChatPanel({ tripId, onReply, disabled }: ChatPanelProps) {
  const history = useChatHistory(tripId)
  const send = useSendMessage(tripId)
  const [text, setText] = useState('')
  const [sourcesByContent, setSourcesByContent] = useState<Record<string, Source[]>>({})
  const listRef = useRef<HTMLOListElement>(null)

  const messages = history.data ?? []
  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages.length, send.isPending])

  function submit(message: string) {
    const trimmed = message.trim()
    if (!trimmed || send.isPending || disabled) {
      return
    }
    send.mutate(trimmed, {
      onSuccess: (reply) => {
        setText('')
        if (reply.sources.length > 0) {
          setSourcesByContent((s) => ({ ...s, [reply.reply]: reply.sources }))
        }
        onReply(reply)
      },
    })
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    submit(text)
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit(text)
    }
  }

  return (
    <section
      className="flex flex-col rounded-xl border border-slate-200 bg-white"
      aria-label="Trò chuyện"
    >
      <h3 className="border-b border-slate-200 px-4 py-2 font-semibold">Hỏi hoặc đổi lịch</h3>

      <ol
        ref={listRef}
        className="max-h-96 min-h-24 space-y-3 overflow-y-auto px-4 py-3"
        aria-live="polite"
      >
        {history.isError && (
          <li className="text-sm text-red-700">Không tải được lịch sử trò chuyện.</li>
        )}
        {messages.length === 0 && !history.isPending && (
          <li className="text-sm text-slate-500">Ví dụ bạn có thể nói:</li>
        )}
        {messages.length === 0 &&
          !history.isPending &&
          EXAMPLES.map((ex) => (
            <li key={ex}>
              <button
                type="button"
                onClick={() => submit(ex)}
                disabled={send.isPending || disabled}
                className="rounded-full border border-slate-300 px-3 py-1 text-left text-sm text-slate-700 hover:border-sky-400 disabled:opacity-50"
              >
                {ex}
              </button>
            </li>
          ))}
        {messages.map((m) => (
          <li key={m.id} className={m.role === 'USER' ? 'flex justify-end' : 'flex justify-start'}>
            <div
              className={`max-w-[90%] rounded-lg px-3 py-2 text-sm ${
                m.role === 'USER' ? 'bg-sky-600 text-white' : 'bg-slate-100 text-slate-800'
              }`}
            >
              {m.role === 'ASSISTANT' && m.intent && (
                <span className="mb-1 block text-xs text-slate-500">
                  {INTENT_LABELS[m.intent]}
                  {m.intent === 'MODIFY_PLAN' && m.tripVersion ? ` · v${m.tripVersion}` : ''}
                </span>
              )}
              {/* Plain text only: replies may quote external data. */}
              <p className="whitespace-pre-wrap">{m.content}</p>
              {m.role === 'ASSISTANT' && sourcesByContent[m.content] && (
                <div className="mt-2 flex flex-wrap gap-1">
                  {sourcesByContent[m.content].map((s) => (
                    <SourceChip key={s.id} source={s} />
                  ))}
                </div>
              )}
            </div>
          </li>
        ))}
        {send.isPending && (
          <li className="text-sm text-slate-500">Đang trả lời… (đổi lịch có thể mất 10–20 giây)</li>
        )}
      </ol>

      {send.isError && (
        <div className="px-4 pb-2">
          <ErrorPanel error={send.error} onRetry={() => submit(send.variables ?? text)} />
        </div>
      )}

      <form onSubmit={onSubmit} className="flex gap-2 border-t border-slate-200 p-3">
        <label htmlFor="chat-input" className="sr-only">
          Tin nhắn
        </label>
        <textarea
          id="chat-input"
          rows={2}
          maxLength={MAX_LENGTH}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          disabled={disabled}
          placeholder={disabled ? 'Đang xem phiên bản cũ' : 'Ví dụ: Bỏ Bà Nà, thêm biển'}
          className="flex-1 resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none disabled:bg-slate-50"
        />
        <button
          type="submit"
          disabled={!text.trim() || send.isPending || disabled}
          className="self-end rounded-lg bg-sky-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          Gửi
        </button>
      </form>
    </section>
  )
}
