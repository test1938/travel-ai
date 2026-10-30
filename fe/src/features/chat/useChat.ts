import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ApiError } from '../../api/client'
import { messageKeys, messagesApi } from '../../api/messages'
import { tripKeys } from '../../api/trips'
import type { ChatMessage, ChatReply } from '../../types/api'

export function useChatHistory(tripId: string) {
  return useQuery<ChatMessage[], ApiError>({
    queryKey: messageKeys.history(tripId),
    queryFn: () => messagesApi.history(tripId),
    staleTime: Infinity,
  })
}

/**
 * Sends a message; on success appends both messages to the cached history and, when the trip changed, replaces
 * the cached plan with the new version.
 */
export function useSendMessage(tripId: string) {
  const queryClient = useQueryClient()
  return useMutation<ChatReply, ApiError, string>({
    mutationFn: (message) => messagesApi.send(tripId, message),
    onSuccess: (reply, message) => {
      const now = new Date().toISOString()
      queryClient.setQueryData<ChatMessage[]>(messageKeys.history(tripId), (old = []) => [
        ...old,
        {
          id: -Date.now(),
          role: 'USER',
          content: message,
          intent: null,
          tripVersion: null,
          createdAt: now,
        },
        {
          id: -Date.now() - 1,
          role: 'ASSISTANT',
          content: reply.reply,
          intent: reply.intent,
          tripVersion: reply.newVersion,
          createdAt: now,
        },
      ])
      if (reply.plan) {
        queryClient.setQueryData(tripKeys.trip(tripId), reply.plan)
      }
    },
  })
}
