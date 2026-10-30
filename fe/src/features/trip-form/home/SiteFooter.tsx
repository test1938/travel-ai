import { Link } from 'react-router-dom'
import { PHOTO_CREDITS } from '../../../lib/photos'

/**
 * "Invitation over a cropped name": the close and the footer in one night field. The name is set larger than
 * the screen and cut by its bottom edge; the credits for every photograph and data source sit above it.
 */
export function SiteFooter() {
  return (
    <footer className="relative mt-[calc(var(--section)/2)] flex min-h-[100svh] flex-col overflow-hidden bg-night px-[var(--margin)] pt-[calc(var(--section)/2)] text-white">
      <div className="grid grid-cols-1 gap-10 lg:grid-cols-12">
        <div className="lg:col-span-7">
          <h2
            data-reveal
            className="text-[length:var(--step-head)] leading-[1.02] font-bold tracking-[-0.035em]"
          >
            Chuyến tới, đi đâu?
          </h2>
          <a href="#plan" data-reveal className="btn-sun mt-10 min-h-14 px-8 text-[18px]">
            Lập lịch ngay
          </a>
        </div>
        <ul className="space-y-3 text-[18px] lg:col-span-3 lg:col-start-10">
          <li>
            <Link to="/demo" className="hover:underline">
              Lịch mẫu Đà Nẵng
            </Link>
          </li>
          <li>
            <a href="#how" className="hover:underline">
              Cách hoạt động
            </a>
          </li>
          <li>
            <a href="#places" className="hover:underline">
              Địa điểm
            </a>
          </li>
        </ul>
      </div>

      <div className="mt-auto pt-24">
        <p className="max-w-[68ch] text-[14px] leading-[1.6] text-[#c3c7cc]">
          Ảnh từ Wikimedia Commons:{' '}
          {PHOTO_CREDITS.map((p, i) => (
            <span key={p.src}>
              <a href={p.page} target="_blank" rel="noreferrer" className="underline">
                {p.place}
              </a>{' '}
              ({p.credit}){i < PHOTO_CREDITS.length - 1 ? '; ' : '. '}
            </span>
          ))}
          Bản đồ ©{' '}
          <a
            href="https://www.openstreetmap.org/copyright"
            target="_blank"
            rel="noreferrer"
            className="underline"
          >
            OpenStreetMap contributors
          </a>{' '}
          (ODbL). Nội dung hướng dẫn từ Wikivoyage và Wikipedia (CC BY-SA 4.0). Thời tiết:
          Open-Meteo. Giá vé có ngày cập nhật, có thể thay đổi.
        </p>
      </div>

      <p
        aria-hidden
        className="pointer-events-none mt-6 -mb-[0.06em] translate-y-[30%] text-[clamp(96px,26vw,440px)] leading-[0.8] font-bold tracking-[-0.055em] whitespace-nowrap text-sun select-none"
      >
        TravelPilot
      </p>
    </footer>
  )
}
