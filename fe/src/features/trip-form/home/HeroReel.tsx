import { useEffect, useRef, useState } from 'react'
import { PHOTOS } from '../../../lib/photos'

/** The reel passes through the three destinations, Hội An and Bà Nà standing for Đà Nẵng. */
const FRAMES = [PHOTOS.hanoi, PHOTOS.hoianStreet, PHOTOS.bana, PHOTOS.halong]

/**
 * Hero, "sentence split by a frame": the title's two halves hold the screen's edges while a reel of the
 * destinations passes through the frame between them as the page scrolls (a sticky stage, no scroll hijack).
 * Under reduced motion the stage is one screen showing the first frame.
 */
export function HeroReel() {
  const section = useRef<HTMLElement>(null)
  const stage = useRef<HTMLDivElement>(null)
  const [current, setCurrent] = useState(0)

  useEffect(() => {
    const el = section.current
    const st = stage.current
    if (!el || !st || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return
    }
    let frame = 0
    const update = () => {
      frame = 0
      const rect = el.getBoundingClientRect()
      const run = rect.height - st.offsetHeight
      const p = run > 0 ? Math.min(1, Math.max(0, -rect.top / run)) : 0
      const step = p * (FRAMES.length - 1)
      st.style.setProperty('--step', step.toFixed(4))
      setCurrent(Math.round(step))
    }
    const onScroll = () => {
      if (!frame) {
        frame = requestAnimationFrame(update)
      }
    }
    frame = requestAnimationFrame(update)
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      cancelAnimationFrame(frame)
    }
  }, [])

  return (
    <section ref={section} className="hero-reel" aria-labelledby="hero-title">
      <div ref={stage} className="hero-stage">
        <div className="hero-window">
          <ul className="hero-strip" aria-label="Ba điểm đến">
            {FRAMES.map((photo, i) => (
              <li key={photo.src} className="hero-frame">
                <img
                  src={photo.src}
                  width={photo.width}
                  height={photo.height}
                  alt={photo.alt}
                  loading={i === 0 ? 'eager' : 'lazy'}
                  fetchPriority={i === 0 ? 'high' : 'auto'}
                />
              </li>
            ))}
          </ul>
        </div>

        <h1 id="hero-title" className="hero-title">
          <span className="hero-half hero-half--start">Lịch trình</span>{' '}
          <span className="hero-half hero-half--end">từng ngày</span>
        </h1>

        <div className="hero-lead">
          <p className="text-[18px] leading-[1.5]">
            TravelPilot xếp lịch đi chơi ở Hà Nội, Đà Nẵng và Hạ Long: địa điểm thật, giờ mở cửa
            thật, giá vé do máy cộng chứ không để AI tự tính.
          </p>
          <a href="#plan" className="btn-sun mt-6">
            Lập lịch cho chuyến của bạn
          </a>
        </div>

        <p className="hero-caption">{FRAMES[current].place}</p>
      </div>
    </section>
  )
}
