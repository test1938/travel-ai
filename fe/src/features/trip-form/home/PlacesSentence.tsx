import { useEffect, useState } from 'react'

/** Real catalog names (data/catalog_*.json): 45 in Hà Nội, 34 around Đà Nẵng, 22 in Quảng Ninh, 101 in all. */
const NAMES = [
  'Văn Miếu – Quốc Tử Giám',
  'Phố cổ Hội An',
  'Sun World Bà Nà Hills',
  'Ngũ Hành Sơn',
  'Du thuyền Vịnh Hạ Long',
  'Nhà hát Múa rối nước Thăng Long',
  'Chợ Hàn',
  'Khu di tích Yên Tử',
]
const OTHERS = 101 - NAMES.length

const reducedMotion = () =>
  typeof window === 'undefined' || window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * "Names as one sentence": the places TravelPilot knows, written out as the proof. One name at a time is lit in
 * full ink; under reduced motion all names stay lit.
 */
export function PlacesSentence() {
  // -1 lights every name: reduced motion, or server rendering
  const [lit, setLit] = useState(() => (reducedMotion() ? -1 : 0))

  useEffect(() => {
    if (reducedMotion()) {
      return
    }
    const timer = setInterval(() => setLit((i) => (i + 1) % NAMES.length), 2000)
    return () => clearInterval(timer)
  }, [])

  return (
    <section
      id="places"
      aria-labelledby="places-title"
      className="scroll-mt-4 px-[var(--margin)] py-[calc(var(--section)/2)]"
    >
      <p
        data-reveal
        className="max-w-[52ch] text-[length:var(--step-statement)] leading-[1.35] font-medium tracking-[-0.01em]"
      >
        <span id="places-title" role="heading" aria-level={2} className="font-bold text-ink">
          101 địa điểm đã kiểm tra,
        </span>{' '}
        {NAMES.map((name, i) => (
          <span
            key={name}
            className={`transition-colors duration-[400ms] ${lit === -1 || lit === i ? 'text-ink' : 'text-[#8a8f96]'}`}
          >
            {name}
            {i < NAMES.length - 1 ? ', ' : ''}
          </span>
        ))}
        <span className="text-[#8a8f96]"> và {OTHERS} nơi khác.</span>
      </p>
    </section>
  )
}
