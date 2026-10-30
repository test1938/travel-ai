import { describe, expect, it } from 'vitest'
import {
  budgetPercent,
  daysSince,
  formatDate,
  formatDayHeading,
  formatDuration,
  formatVnd,
  parseVnd,
  priceLabel,
} from './format'

describe('formatVnd', () => {
  it('uses Vietnamese thousands separators and đ', () => {
    expect(formatVnd(2_800_000)).toBe('2.800.000đ')
    expect(formatVnd(0)).toBe('0đ')
  })
})

describe('priceLabel', () => {
  it('marks estimates with ~ and free places as Miễn phí', () => {
    expect(priceLabel({ costVnd: 208_000, costEstimated: true })).toBe('~208.000đ')
    expect(priceLabel({ costVnd: 2_800_000, costEstimated: false })).toBe('2.800.000đ')
    expect(priceLabel({ costVnd: 0, costEstimated: false })).toBe('Miễn phí')
    expect(priceLabel({ costVnd: 0, costEstimated: true })).toBe('Miễn phí (ước tính)')
  })
})

describe('dates', () => {
  it('formats ISO dates the Vietnamese way', () => {
    expect(formatDate('2026-10-12')).toBe('12/10/2026')
    expect(formatDate('not a date')).toBe('not a date')
    expect(formatDayHeading('2026-11-12')).toBe('Thứ Năm, 12/11')
  })

  it('counts days since a price check', () => {
    expect(daysSince('2026-10-12', new Date('2026-10-30T12:00:00'))).toBe(18)
    expect(daysSince(null)).toBeNull()
  })
})

describe('budget helpers', () => {
  it('computes the used share, possibly above 100', () => {
    expect(budgetPercent(5_064_000, 6_000_000)).toBe(84)
    expect(budgetPercent(6_806_000, 6_000_000)).toBe(113)
    expect(budgetPercent(1, 0)).toBe(0)
  })

  it('parses typed VND amounts', () => {
    expect(parseVnd('6.000.000')).toBe(6_000_000)
    expect(parseVnd('6000000đ')).toBe(6_000_000)
    expect(parseVnd('')).toBeNaN()
  })

  it('formats durations', () => {
    expect(formatDuration(45)).toBe('45 phút')
    expect(formatDuration(480)).toBe('8 giờ')
    expect(formatDuration(150)).toBe('2 giờ 30 phút')
  })
})
