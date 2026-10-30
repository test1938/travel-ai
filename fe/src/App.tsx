import { Route, Routes } from 'react-router-dom'
import { AttributionFooter } from './components/AttributionFooter'
import { ChatPlayground } from './features/chat/ChatPlayground'

export default function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-200 bg-white px-4 py-3">
        <h1 className="text-lg font-semibold">TravelPilot AI</h1>
      </header>
      <main className="flex-1 px-4 py-6">
        <Routes>
          <Route path="/" element={<ChatPlayground />} />
          <Route path="*" element={<p className="text-center">Không tìm thấy trang.</p>} />
        </Routes>
      </main>
      <AttributionFooter />
    </div>
  )
}
