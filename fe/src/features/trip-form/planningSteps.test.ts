import { describe, expect, it } from 'vitest'
import { serverStepStates, stepStates } from './planningSteps'

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

describe('serverStepStates', () => {
  it('marks earlier steps done and the reported one active', () => {
    expect(serverStepStates('retrieve')).toEqual(['done', 'done', 'active', 'pending', 'pending'])
    expect(serverStepStates('assemble')).toEqual(['done', 'done', 'done', 'done', 'active'])
  })

  it('ignores unknown steps so the simulated progress keeps running', () => {
    expect(serverStepStates('teleport')).toBeNull()
  })
})
