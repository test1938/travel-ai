/**
 * Simulated progress for the 10-20 s planning call (replaced by real server events in T9.01).
 * Each step becomes active after `atMs`; the last step stays active until the response arrives.
 */
export const PLANNING_STEPS = [
  { label: 'Lọc địa điểm phù hợp', atMs: 0 },
  { label: 'Chấm điểm theo sở thích và ngân sách', atMs: 1_500 },
  { label: 'Tìm thông tin và nguồn tham khảo', atMs: 3_000 },
  { label: 'AI soạn lịch từng ngày', atMs: 4_500 },
  { label: 'Kiểm tra giờ mở cửa và ngân sách', atMs: 14_000 },
] as const

export type StepState = 'done' | 'active' | 'pending'

export function stepStates(elapsedMs: number): StepState[] {
  return PLANNING_STEPS.map((step, i) => {
    const next = PLANNING_STEPS[i + 1]
    if (elapsedMs < step.atMs) {
      return 'pending'
    }
    return next && elapsedMs >= next.atMs ? 'done' : 'active'
  })
}
