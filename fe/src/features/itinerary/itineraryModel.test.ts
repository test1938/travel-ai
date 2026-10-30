import { describe, expect, it } from 'vitest'
import type { Budget, Source, Warning } from '../../types/api'
import {
  budgetLevel,
  disclaimer,
  isPriceStale,
  safeUrl,
  scoreRows,
  sourcesFor,
  visibleWarnings,
  warningSeverity,
} from './itineraryModel'

const budget = (total: number, limit: number, overBudget = total > limit): Budget => ({
  totalVnd: total,
  limitVnd: limit,
  byType: {},
  overBudget,
})

const warning = (code: string): Warning => ({ code, message: code, dayIndex: null, placeId: null })

describe('budgetLevel', () => {
  it('is ok, near or over', () => {
    expect(budgetLevel(budget(5_064_000, 6_000_000))).toBe('ok')
    expect(budgetLevel(budget(5_500_000, 6_000_000))).toBe('near')
    expect(budgetLevel(budget(6_806_000, 6_000_000))).toBe('over')
  })
})

describe('warnings', () => {
  it('lists errors first, keeps order otherwise and hides the disclaimer', () => {
    const list = [
      warning('WEATHER_UNAVAILABLE'),
      warning('FALLBACK_USED'),
      warning('PRICE_ESTIMATE_DISCLAIMER'),
      warning('OVER_BUDGET'),
      warning('DAYTRIP_LUNCH'),
    ]
    expect(visibleWarnings(list).map((w) => w.code)).toEqual([
      'OVER_BUDGET',
      'FALLBACK_USED',
      'WEATHER_UNAVAILABLE',
      'DAYTRIP_LUNCH',
    ])
    expect(disclaimer(list)).toBe('PRICE_ESTIMATE_DISCLAIMER')
    expect(warningSeverity('SOMETHING_NEW')).toBe('info')
  })
})

describe('prices and sources', () => {
  it('flags prices checked more than 180 days ago', () => {
    const today = new Date('2026-10-30T00:00:00')
    expect(isPriceStale('2026-10-12', today)).toBe(false)
    expect(isPriceStale('2026-03-01', today)).toBe(true)
    expect(isPriceStale(null, today)).toBe(false)
  })

  it('resolves cited sources in order and ignores unknown ids', () => {
    const s = (id: string): Source => ({
      id,
      docId: id,
      title: id,
      url: null,
      license: null,
      snippet: '',
    })
    expect(sourcesFor(['ev-2', 'ev-9', 'ev-1'], [s('ev-1'), s('ev-2')]).map((x) => x.id)).toEqual([
      'ev-2',
      'ev-1',
    ])
  })

  it('only allows http(s) links', () => {
    expect(safeUrl('https://www.openstreetmap.org/way/1')).toBe(
      'https://www.openstreetmap.org/way/1',
    )
    expect(safeUrl('javascript:alert(1)')).toBeNull()
    expect(safeUrl('not a url')).toBeNull()
    expect(safeUrl(null)).toBeNull()
  })
})

describe('scoreRows', () => {
  it('turns the breakdown into labelled percentages', () => {
    const rows = scoreRows({
      total: 0.77,
      preference: 0.5,
      budget: 1,
      distance: 0.712,
      schedule: 1,
      group: 1,
      popularity: 0.64,
      rainBonus: 0,
    })
    expect(rows[0]).toEqual({ key: 'preference', label: 'Hợp sở thích', percent: 50 })
    expect(rows.find((r) => r.key === 'distance')?.percent).toBe(71)
    expect(rows).toHaveLength(6)
  })
})
