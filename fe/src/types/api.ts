/** Error codes returned by the backend in ProblemDetail.code (be: common/error/ErrorCode.java). */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'INPUT_TOO_LONG'
  | 'NOT_FOUND'
  | 'RATE_LIMITED'
  | 'LLM_ERROR'
  | 'LLM_TIMEOUT'
  | 'PLAN_INVALID_AFTER_REPAIR'
  | 'INTERNAL'

/** RFC 9457 ProblemDetail as produced by GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  code?: ErrorCode
  requestId?: string
  errors?: Record<string, string>
}

export interface AiChatRequest {
  message: string
}

export interface AiChatResponse {
  answer: string
  model: string | null
  latencyMs: number
}
