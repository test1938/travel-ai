import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { TripForm } from '../trip-form/TripForm'
import { DEMO_PLAN } from './demoPlan'
import { TripView } from './TripView'

/** Smoke tests: components render without a browser and show the key information. */
function render(node: React.ReactNode): string {
  // React separates adjacent text nodes with <!-- --> markers in server rendering.
  return renderToString(<MemoryRouter>{node}</MemoryRouter>).replace(/<!-- -->/g, '')
}

describe('TripView', () => {
  it('shows days, prices with ~ for estimates, budget and sources', () => {
    const html = render(<TripView plan={DEMO_PLAN} />)
    expect(html).toContain('Đà Nẵng 3 ngày cho gia đình')
    expect(html).toContain('Sun World Bà Nà Hills')
    expect(html).toContain('2.800.000đ')
    expect(html).toContain('~208.000đ')
    expect(html).toContain('Miễn phí')
    expect(html).toContain('5.064.000đ')
    expect(html).toContain('Dự báo mưa')
    expect(html).toContain('Giá cập nhật 12/10/2026')
    expect(html).toContain('Nguồn #1')
    expect(html).toContain('ăn trưa tại điểm đến')
    expect(html).toContain('chưa gồm khách sạn')
    expect(html).not.toContain('Vượt ngân sách')
  })

  it('shows a red alert when over budget', () => {
    const over = {
      ...DEMO_PLAN,
      budget: { ...DEMO_PLAN.budget, limitVnd: 4_000_000, overBudget: true },
      warnings: [
        ...DEMO_PLAN.warnings,
        {
          code: 'OVER_BUDGET',
          message: 'Tổng chi phí vượt ngân sách 1.064.000đ.',
          dayIndex: null,
          placeId: null,
        },
      ],
    }
    const html = render(<TripView plan={over} />)
    expect(html).toContain('Vượt ngân sách 1.064.000đ')
    expect(html).toContain('bg-red-600')
  })

  it('offers a jump to the chat on phones only when there is a chat', () => {
    expect(render(<TripView plan={DEMO_PLAN} />)).not.toContain('href="#trip-aside"')
    const withChat = render(<TripView plan={DEMO_PLAN} aside={<p>chat</p>} />)
    expect(withChat).toContain('href="#trip-aside"')
    expect(withChat).toContain('lg:hidden')
    expect(withChat).toContain('id="trip-aside"')
  })
})

describe('TripForm', () => {
  it('renders the budget label and all preference chips', () => {
    const html = render(<TripForm onSubmit={() => {}} submitting={false} />)
    expect(html).toContain('Ngân sách vui chơi và ăn uống')
    expect(html).toContain('Công viên giải trí')
    expect(html).toContain('Lập lịch trình')
  })

  it('answers the sentence with a live preview of what the plan will respect', () => {
    const html = render(<TripForm onSubmit={() => {}} submitting={false} />)
    // defaults: Đà Nẵng, 3 days, 2 adults, 6.000.000đ, relaxed pace (2 activities a day)
    expect(html).toContain('Lịch Đà Nẵng 3 ngày')
    expect(html).toContain('2.000.000đ cho cả nhóm')
    expect(html).toContain('Tối đa 6 cả chuyến, 2 mỗi ngày')
  })

  it('shows the planning status instead of the preview while a plan is being made', () => {
    const html = render(
      <TripForm onSubmit={() => {}} submitting status={<p>Đang lập lịch trình…</p>} />,
    )
    expect(html).toContain('Đang lập lịch…')
    expect(html).not.toContain('Lịch Đà Nẵng 3 ngày')
  })
})
