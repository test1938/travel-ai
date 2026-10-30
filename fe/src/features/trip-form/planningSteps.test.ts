import { describe, expect, it } from 'vitest'
import { stepStates } from './planningSteps'

describe('stepStates', () => {
  it('starts with the first step active', () => {
    expect(stepStates(0)).toEqual(['active', 'pending', 'pending', 'pending', 'pending'])
  })

  it('moves forward over time', () => {
    expect(stepStates(3_200)).toEqual(['done', 'done', 'active', 'pending', 'pending'])
  })

  it('keeps the last step active until the answer arrives', () => {
    expect(stepStates(60_000)).toEqual(['done', 'done', 'done', 'done', 'active'])
  })
})
