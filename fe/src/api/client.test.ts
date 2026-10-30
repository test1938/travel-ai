import { describe, expect, it } from 'vitest'
import { parseSseFrames } from './client'

describe('parseSseFrames', () => {
  it('parses complete frames in the format Spring SseEmitter writes', () => {
    const { events, rest } = parseSseFrames(
      'event:step\ndata:{"step":"weather"}\n\nevent:result\ndata:{"tripId":"t1"}\n\n',
    )
    expect(events).toEqual([
      { event: 'step', data: '{"step":"weather"}' },
      { event: 'result', data: '{"tripId":"t1"}' },
    ])
    expect(rest).toBe('')
  })

  it('keeps an incomplete frame for the next chunk', () => {
    const first = parseSseFrames('event:step\ndata:{"step":"llm"}\n\nevent:res')
    expect(first.events).toHaveLength(1)
    const second = parseSseFrames(first.rest + 'ult\ndata:{"ok":true}\n\n')
    expect(second.events).toEqual([{ event: 'result', data: '{"ok":true}' }])
  })

  it('accepts CRLF line endings and a space after the colon', () => {
    const { events } = parseSseFrames('event: error\r\ndata: {"code":"LLM_TIMEOUT"}\r\n\r\n')
    expect(events).toEqual([{ event: 'error', data: '{"code":"LLM_TIMEOUT"}' }])
  })
})
