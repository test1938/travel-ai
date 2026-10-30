import { clearGuestToken, getGuestToken } from '../lib/guest'
import type { ErrorCode, ProblemDetail } from '../types/api'

const MESSAGES: Record<ErrorCode | 'NETWORK', string> = {
  VALIDATION_FAILED: 'Dữ liệu chưa hợp lệ, vui lòng kiểm tra lại.',
  INPUT_TOO_LONG: 'Nội dung quá dài.',
  UNAUTHORIZED: 'Phiên làm việc đã hết hạn, vui lòng tải lại trang.',
  NOT_FOUND: 'Không tìm thấy dữ liệu.',
  RATE_LIMITED: 'Bạn gửi quá nhiều yêu cầu, vui lòng thử lại sau ít phút.',
  LLM_ERROR: 'Dịch vụ AI đang gặp lỗi, vui lòng thử lại.',
  LLM_TIMEOUT: 'Dịch vụ AI phản hồi quá lâu, vui lòng thử lại.',
  PLAN_INVALID_AFTER_REPAIR: 'Không tạo được lịch trình hợp lệ.',
  INTERNAL: 'Đã có lỗi xảy ra.',
  NETWORK: 'Không kết nối được máy chủ. Kiểm tra mạng hoặc backend.',
}

/** Error thrown by every API call; `message` is already a user-facing Vietnamese text. */
export class ApiError extends Error {
  readonly status: number
  readonly code: ErrorCode | 'NETWORK'
  readonly requestId?: string
  readonly fieldErrors?: Record<string, string>

  constructor(
    status: number,
    code: ErrorCode | 'NETWORK',
    requestId?: string,
    fieldErrors?: Record<string, string>,
  ) {
    super(MESSAGES[code] ?? MESSAGES.INTERNAL)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
    this.fieldErrors = fieldErrors
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const headerRequestId = response.headers.get('X-Request-Id') ?? undefined
  try {
    const problem = (await response.json()) as ProblemDetail
    return new ApiError(
      response.status,
      problem.code ?? (response.status === 404 ? 'NOT_FOUND' : 'INTERNAL'),
      problem.requestId ?? headerRequestId,
      problem.errors,
    )
  } catch {
    // A proxy error page (e.g. backend down behind Vite) is not JSON.
    return new ApiError(
      response.status,
      response.status >= 502 ? 'NETWORK' : 'INTERNAL',
      headerRequestId,
    )
  }
}

async function send(method: string, path: string, body?: unknown): Promise<Response> {
  let token: string
  try {
    token = await getGuestToken()
  } catch {
    throw new ApiError(0, 'NETWORK')
  }
  try {
    return await fetch(path, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK')
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response = await send(method, path, body)
  if (response.status === 401) {
    // Token expired or signed with an old server key: get a fresh one and retry once.
    clearGuestToken()
    response = await send(method, path, body)
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as T
}

/** One Server-Sent Event: `event:` name and the joined `data:` lines. */
export interface SseEvent {
  event: string
  data: string
}

/** Splits complete SSE frames (separated by a blank line) off the buffer; the rest waits for more bytes. */
export function parseSseFrames(buffer: string): { events: SseEvent[]; rest: string } {
  const frames = buffer.split(/\r?\n\r?\n/)
  const rest = frames.pop() ?? ''
  const events = frames
    .map((frame) => {
      let event = 'message'
      const data: string[] = []
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith('event:')) {
          event = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          data.push(line.slice(5).replace(/^ /, ''))
        }
      }
      return { event, data: data.join('\n') }
    })
    .filter((e) => e.data !== '' || e.event !== 'message')
  return { events, rest }
}

/**
 * POST that answers with Server-Sent Events (EventSource cannot send a body or an Authorization header):
 * `onEvent` gets every event before `result`; resolves with the `result` data, rejects on `error`.
 */
async function stream<T>(path: string, body: unknown, onEvent: (e: SseEvent) => void): Promise<T> {
  let response = await send('POST', path, body)
  if (response.status === 401) {
    clearGuestToken()
    response = await send('POST', path, body)
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (!response.body) {
    throw new ApiError(0, 'NETWORK')
  }
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  for (;;) {
    let chunk: ReadableStreamReadResult<string>
    try {
      chunk = await reader.read()
    } catch {
      throw new ApiError(0, 'NETWORK')
    }
    if (chunk.done) {
      throw new ApiError(0, 'NETWORK') // stream closed without a result
    }
    const parsed = parseSseFrames(buffer + chunk.value)
    buffer = parsed.rest
    for (const e of parsed.events) {
      if (e.event === 'result') {
        void reader.cancel()
        return JSON.parse(e.data) as T
      }
      if (e.event === 'error') {
        const problem = JSON.parse(e.data) as { code?: ErrorCode; requestId?: string | null }
        throw new ApiError(500, problem.code ?? 'INTERNAL', problem.requestId ?? undefined)
      }
      onEvent(e)
    }
  }
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body: unknown) => request<T>('POST', path, body),
  stream,
}
