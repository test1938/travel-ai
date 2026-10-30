import { Link, Route, Routes, useLocation } from 'react-router-dom'
import { AttributionFooter } from './components/AttributionFooter'
import { Wordmark } from './components/Wordmark'
import { ChatPlayground } from './features/chat/ChatPlayground'
import { DemoPage } from './features/itinerary/DemoPage'
import { TripPage } from './features/itinerary/TripPage'
import { HomePage } from './features/trip-form/HomePage'

/**
 * Shell: one plain bar (wordmark, three links, one action). The home page runs full-bleed and closes with its
 * own footer; the itinerary screens sit on the sand ground in a centred column.
 */
export default function App() {
  const home = useLocation().pathname === '/'
  return (
    <div className={`flex min-h-svh flex-col ${home ? 'bg-white' : 'bg-sand'}`}>
      <header className="flex h-16 items-center justify-between gap-4 bg-white px-[var(--margin)] text-[16px]">
        <Link to="/" aria-label="TravelPilot, trang chủ" className="rounded-tp">
          <Wordmark />
        </Link>
        <nav aria-label="Chính" className="flex items-center gap-6">
          <a href="/#how" className="hidden text-ink hover:underline md:inline">
            Cách hoạt động
          </a>
          <a href="/#places" className="hidden text-ink hover:underline md:inline">
            Địa điểm
          </a>
          <Link to="/demo" className="hidden text-ink hover:underline sm:inline">
            Lịch mẫu
          </Link>
          <a href="/#plan" className="btn-sun min-h-10 px-4 text-[16px]">
            Lập lịch
          </a>
        </nav>
      </header>
      <main className={home ? 'flex-1' : 'flex-1 px-[var(--margin)] py-8'}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/trips/:id" element={<TripPage />} />
          <Route path="/demo" element={<DemoPage />} />
          <Route path="/playground" element={<ChatPlayground />} />
          <Route
            path="*"
            element={
              <div className="mx-auto max-w-xl py-16">
                <h1 className="text-2xl font-bold">Không tìm thấy trang</h1>
                <Link to="/" className="mt-4 inline-block underline">
                  Về trang lập lịch
                </Link>
              </div>
            }
          />
        </Routes>
      </main>
      {!home && <AttributionFooter />}
    </div>
  )
}
