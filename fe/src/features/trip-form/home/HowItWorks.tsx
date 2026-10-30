import type { CSSProperties } from 'react'

/**
 * "Stages as one bar": the planning progress bar the visitor will see, enlarged into the diagram. Each segment
 * is as wide as its step takes (12% minimum), so it shows at a glance that the model's draft is most of the
 * wait and everything around it (weather, filtering, prices) is the backend's quick, checked work.
 * Durations are approximate for a 3-day plan: the model call dominates, the rest is in-memory work and caches.
 */
const STAGES = [
  {
    took: 'Nửa giây',
    flex: '0 0 12%',
    tone: 'bg-stone',
    clause: 'Đầu tiên, xem dự báo từng ngày; ngày mưa ưu tiên chỗ trong nhà.',
  },
  {
    took: 'Tức thì',
    flex: '0 0 12%',
    tone: 'bg-[#d6d6d0]',
    clause: 'Sau đó, loại chỗ đóng cửa, quá ngân sách hay không hợp tuổi bé.',
  },
  {
    took: 'Một giây',
    flex: '0 0 12%',
    tone: 'bg-[#c4c4bd]',
    clause: 'Tiếp theo, tìm thông tin và nguồn cho từng điểm.',
  },
  {
    took: '10–15 giây',
    flex: '1 1 0',
    tone: 'bg-sun',
    clause:
      'Rồi AI chọn điểm cho từng buổi và viết lý do; sai quy tắc thì tự sửa, quá hai lần thì máy xếp thay.',
  },
  {
    took: 'Tức thì',
    flex: '0 0 12%',
    tone: 'bg-ink text-white',
    clause: 'Cuối cùng, máy tính giờ đi, cộng tiền và kiểm lại 14 quy tắc.',
  },
] as const

export function HowItWorks() {
  return (
    <section
      id="how"
      aria-labelledby="how-title"
      className="scroll-mt-4 px-[var(--margin)] py-[calc(var(--section)/2)]"
    >
      <h2
        id="how-title"
        data-reveal
        className="text-[length:var(--step-head)] leading-[1.02] font-bold tracking-[-0.035em] text-balance"
      >
        Mười lăm giây, năm bước
      </h2>
      <p data-reveal className="mt-6 max-w-[56ch] text-[length:var(--step-body)] leading-[1.55]">
        Thanh tiến trình bạn thấy khi bấm lập lịch, phóng to. Phần màu vàng là lúc AI làm việc; mọi
        thứ quanh nó là máy tính toán và kiểm tra.
      </p>

      <ol
        data-ui
        aria-label="Các bước lập lịch"
        className="mt-16 flex flex-col gap-1.5 lg:h-[22vh] lg:min-h-[150px] lg:flex-row"
      >
        {STAGES.map((s, i) => (
          <li
            key={s.clause}
            data-reveal
            style={{ flex: s.flex, '--i': i } as CSSProperties}
            className={`flex flex-col rounded-tp p-5 ${s.tone} ${s.flex.startsWith('1') ? 'min-h-[220px] lg:min-h-0' : 'min-h-[72px] lg:min-h-0'}`}
          >
            <span className="text-[22px] leading-tight font-bold lg:text-[length:var(--step-statement)] tracking-[-0.01em]">
              {s.took}
            </span>
            <span className="mt-2 block text-[18px] leading-[1.45] lg:sr-only">{s.clause}</span>
          </li>
        ))}
      </ol>
      <div aria-hidden className="mt-4 hidden gap-1.5 lg:flex">
        {STAGES.map((s) => (
          <p
            key={s.clause}
            style={{ flex: s.flex }}
            className="pr-3 text-[18px] leading-[1.45] last:pr-0"
          >
            {s.clause}
          </p>
        ))}
      </div>
    </section>
  )
}
