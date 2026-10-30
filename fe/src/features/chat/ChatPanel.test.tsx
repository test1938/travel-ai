import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { messageKeys } from '../../api/messages'
import { DEMO_PLAN } from '../itinerary/demoPlan'
import { TripView } from '../itinerary/TripView'
import { MemoryRouter } from 'react-router-dom'
import { ChatPanel } from './ChatPanel'
import { itemKey } from './planDiff'

function html(node: React.ReactNode, client = new QueryClient()): string {
  return renderToString(
    <QueryClientProvider client={client}>
      <MemoryRouter>{node}</MemoryRouter>
    </QueryClientProvider>,
  ).replace(/<!-- -->/g, '')
}

describe('ChatPanel', () => {
  it('shows stored messages with intent and version labels', () => {
    const client = new QueryClient()
    client.setQueryData(messageKeys.history('t1'), [
      {
        id: 1,
        role: 'USER',
        content: 'Ngày 2 trời mưa',
        intent: null,
        tripVersion: null,
        createdAt: '',
      },
      {
        id: 2,
        role: 'ASSISTANT',
        content: 'Đã cập nhật lịch (phiên bản 2)',
        intent: 'MODIFY_PLAN',
        tripVersion: 2,
        createdAt: '',
      },
    ])
    const out = html(<ChatPanel tripId="t1" onReply={() => {}} />, client)
    expect(out).toContain('Ngày 2 trời mưa')
    expect(out).toContain('Đổi lịch · v2')
    expect(out).toContain('Hỏi hoặc đổi lịch')
  })

  it('offers example requests when the chat is empty', () => {
    const client = new QueryClient()
    client.setQueryData(messageKeys.history('t2'), [])
    expect(html(<ChatPanel tripId="t2" onReply={() => {}} />, client)).toContain(
      'Ngày 2 trời mưa, đổi sang hoạt động trong nhà',
    )
  })

  it('is read-only while an old version is shown', () => {
    const client = new QueryClient()
    client.setQueryData(messageKeys.history('t3'), [])
    expect(html(<ChatPanel tripId="t3" onReply={() => {}} disabled />, client)).toContain(
      'Đang xem phiên bản cũ',
    )
  })
})

describe('highlight', () => {
  it('marks changed items on the itinerary', () => {
    const out = html(
      <TripView plan={DEMO_PLAN} highlight={new Set([itemKey(2, 'danang-way-302872188')])} />,
    )
    expect(out.match(/data-highlighted="true"/g)).toHaveLength(1)
  })
})
