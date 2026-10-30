import { describe, expect, it } from 'vitest'
import {
  defaultValues,
  fromServerErrors,
  toRequest,
  todayInVietnam,
  validate,
  type TripFormValues,
} from './tripFormModel'

/** 2026-10-30 10:00 in Vietnam (03:00 UTC). */
const NOW = new Date('2026-10-30T03:00:00Z')

function family(overrides: Partial<TripFormValues> = {}): TripFormValues {
  return {
    destination: 'DANANG',
    startDate: '2026-11-12',
    days: 3,
    adults: 2,
    childrenAges: ['6'],
    budget: '6.000.000',
    preferences: ['BEACH', 'THEME-PARK'],
    pace: 'RELAXED',
    notes: '',
    ...overrides,
  }
}

describe('validate', () => {
  it('accepts the family persona', () => {
    expect(validate(family(), NOW)).toEqual({})
  })

  it('uses Vietnam time for today', () => {
    // 23:30 UTC on Oct 29 is already Oct 30 in Vietnam.
    expect(todayInVietnam(new Date('2026-10-29T23:30:00Z'))).toBe('2026-10-30')
    expect(validate(family({ startDate: '2026-10-29' }), NOW).startDate).toBeDefined()
    expect(validate(family({ startDate: '2026-10-30' }), NOW).startDate).toBeUndefined()
  })

  it('checks ranges like the backend', () => {
    expect(validate(family({ days: 6 }), NOW).days).toBeDefined()
    expect(validate(family({ days: 0 }), NOW).days).toBeDefined()
    expect(validate(family({ adults: 0 }), NOW).adults).toBeDefined()
    expect(validate(family({ childrenAges: ['18'] }), NOW).childrenAges).toBeDefined()
    expect(validate(family({ childrenAges: [''] }), NOW).childrenAges).toBeDefined()
    expect(validate(family({ childrenAges: ['0', '17'] }), NOW).childrenAges).toBeUndefined()
    expect(validate(family({ budget: '' }), NOW).budget).toBeDefined()
    expect(validate(family({ budget: '0' }), NOW).budget).toBeDefined()
    expect(validate(family({ notes: 'x'.repeat(201) }), NOW).notes).toBeDefined()
    expect(
      validate(
        family({
          preferences: ['BEACH', 'NATURE', 'CULTURE', 'HISTORY', 'MUSEUM', 'FOOD', 'PHOTO'],
        }),
        NOW,
      ).preferences,
    ).toBeDefined()
  })
})

describe('toRequest', () => {
  it('builds the API request with numbers and a single trimmed note', () => {
    expect(toRequest(family({ notes: '  Bé hay say xe ' }))).toEqual({
      destination: 'DANANG',
      startDate: '2026-11-12',
      days: 3,
      adults: 2,
      childrenAges: [6],
      budgetVnd: 6_000_000,
      preferences: ['BEACH', 'THEME-PARK'],
      pace: 'RELAXED',
      notes: ['Bé hay say xe'],
    })
    expect(toRequest(family()).notes).toEqual([])
  })
})

describe('fromServerErrors', () => {
  it('maps backend field names onto form fields', () => {
    expect(
      fromServerErrors({ budgetVnd: 'x', days: 'y', 'childrenAges[0]': 'z', other: 'w' }),
    ).toEqual({
      budget: 'x',
      days: 'y',
      childrenAges: 'z',
      form: 'w',
    })
    expect(fromServerErrors(undefined)).toEqual({})
  })
})

describe('defaultValues', () => {
  it('starts two weeks from today', () => {
    expect(defaultValues(NOW).startDate).toBe('2026-11-13')
  })
})
