const KEY = 'travelpilot.guestToken'
/** Renew a day before expiry so a long session never sends an expired token. */
const RENEW_BEFORE_MS = 24 * 60 * 60 * 1000

interface StoredToken {
  token: string
  expiresAt: string
}

/** In-memory copy, also the fallback when localStorage is unavailable (private mode, blocked storage). */
let memory: StoredToken | null = null
let pending: Promise<string> | null = null

function read(): StoredToken | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as StoredToken
      if (typeof parsed.token === 'string' && typeof parsed.expiresAt === 'string') {
        return parsed
      }
    }
  } catch {
    // ignore: fall back to memory
  }
  return memory
}

function write(value: StoredToken | null) {
  memory = value
  try {
    if (value) {
      localStorage.setItem(KEY, JSON.stringify(value))
    } else {
      localStorage.removeItem(KEY)
    }
  } catch {
    // memory copy is enough for this tab
  }
}

/** True when the token is still usable for at least `marginMs`. */
export function isFresh(stored: StoredToken | null, now: number, marginMs = 0): boolean {
  return stored != null && Date.parse(stored.expiresAt) - marginMs > now
}

async function fetchToken(current: StoredToken | null): Promise<string> {
  const usable = isFresh(current, Date.now())
  const response = await fetch('/api/v1/guest', {
    method: 'POST',
    headers: usable && current ? { Authorization: `Bearer ${current.token}` } : {},
  })
  if (!response.ok) {
    throw new Error(`guest token: HTTP ${response.status}`)
  }
  const body = (await response.json()) as StoredToken
  write(body)
  return body.token
}

/**
 * The anonymous guest token sent as `Authorization: Bearer`. Created on first use and renewed (same guest,
 * so trips stay reachable) shortly before it expires. Concurrent callers share one request.
 */
export function getGuestToken(): Promise<string> {
  const stored = read()
  if (isFresh(stored, Date.now(), RENEW_BEFORE_MS) && stored) {
    return Promise.resolve(stored.token)
  }
  pending ??= fetchToken(stored).finally(() => {
    pending = null
  })
  return pending
}

/** Drops a token the server rejected; the next call creates a new guest. */
export function clearGuestToken() {
  write(null)
}
