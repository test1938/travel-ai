import { useMutation } from '@tanstack/react-query'
import { api, type ApiError } from '../../api/client'
import type { AiChatRequest, AiChatResponse } from '../../types/api'

/** Sends one message to the walking-skeleton endpoint POST /api/v1/ai/chat. */
export function useAiChat() {
  return useMutation<AiChatResponse, ApiError, AiChatRequest>({
    mutationFn: (req) => api.post<AiChatResponse>('/api/v1/ai/chat', req),
  })
}
