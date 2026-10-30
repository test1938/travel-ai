/**
 * Planning steps as reported by the server stream (T9.01). Until the first server event arrives (or if a proxy
 * buffers the stream) the progress is simulated: each step becomes active after `atMs`.
 */
export const PLANNING_STEPS = [
  { id: 'weather', label: 'Xem dự báo thời tiết', atMs: 0 },
  { id: 'candidates', label: 'Lọc và chấm điểm địa điểm', atMs: 1_000 },
  { id: 'retrieve', label: 'Tìm thông tin và nguồn tham khảo', atMs: 2_500 },
  { id: 'llm', label: 'AI soạn lịch và tự kiểm tra', atMs: 4_000 },
  { id: 'assemble', label: 'Tính giờ đi và ngân sách', atMs: 15_000 },
] as const

export type StepState = 'done' | 'active' | 'pending'

/** Simulated states after `elapsedMs`; the last step stays active until the answer arrives. */
export function stepStates(elapsedMs: number): StepState[] {
  return PLANNING_STEPS.map((step, i) => {
    const next = PLANNING_STEPS[i + 1]
    if (elapsedMs < step.atMs) {
      return 'pending'
    }
    return next && elapsedMs >= next.atMs ? 'done' : 'active'
  })
}

/** Real states: steps before the one the server just started are done; null for an unknown step id. */
export function serverStepStates(stepId: string): StepState[] | null {
  const active = PLANNING_STEPS.findIndex((s) => s.id === stepId)
  if (active < 0) {
    return null
  }
  return PLANNING_STEPS.map((_, i) => (i < active ? 'done' : i === active ? 'active' : 'pending'))
}
