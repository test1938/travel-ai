import { describe, expect, it } from 'vitest'
import { isFresh } from './guest'

describe('guest token freshness', () => {
  const now = Date.parse('2026-10-30T00:00:00Z')
  const token = { token: 't', expiresAt: '2026-11-06T00:00:00Z' }

  it('is fresh until the expiry minus the margin', () => {
    expect(isFresh(token, now)).toBe(true)
    expect(isFresh(token, Date.parse('2026-11-05T12:00:00Z'), 24 * 3600 * 1000)).toBe(false)
    expect(isFresh(token, Date.parse('2026-11-06T00:00:00Z'))).toBe(false)
  })

  it('treats a missing token as stale', () => {
    expect(isFresh(null, now)).toBe(false)
  })
})
