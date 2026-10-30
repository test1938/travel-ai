import type { ChatMessage, ChatReply } from '../types/api'
import { api } from './client'

export const messagesApi = {
  history: (tripId: string) =>
    api.get<ChatMessage[]>(`/api/v1/trips/${encodeURIComponent(tripId)}/messages`),
  send: (tripId: string, message: string) =>
    api.post<ChatReply>(`/api/v1/trips/${encodeURIComponent(tripId)}/messages`, { message }),
}

export const messageKeys = {
  history: (tripId: string) => ['messages', tripId] as const,
}
