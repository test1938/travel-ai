const KEY = 'travelpilot.guestId'

/**
 * Anonymous id sent as X-Guest-Id so the backend knows who owns a trip (until guest tokens, T6.01).
 * Stored in localStorage; falls back to an in-memory id when storage is unavailable.
 */
let memoryId: string | null = null

export function getGuestId(): string {
  try {
    const stored = localStorage.getItem(KEY)
    if (stored && /^[A-Za-z0-9-]{1,64}$/.test(stored)) {
      return stored
    }
    const id = crypto.randomUUID()
    localStorage.setItem(KEY, id)
    return id
  } catch {
    memoryId ??= crypto.randomUUID()
    return memoryId
  }
}
