/**
 * The TravelPilot mark: one day of an itinerary, morning, afternoon and evening, as three bars on a sun-yellow
 * tile. Drawn for this product; it borrows no other brand's symbol.
 */
export function Mark({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden focusable="false">
      <rect width="24" height="24" rx="3" fill="#ffc20e" />
      <rect x="5" y="6" width="14" height="2.6" rx="1" fill="#212529" />
      <rect x="5" y="10.7" width="9" height="2.6" rx="1" fill="#212529" />
      <rect x="5" y="15.4" width="11.5" height="2.6" rx="1" fill="#212529" />
    </svg>
  )
}

export function Wordmark() {
  return (
    <span className="inline-flex items-center gap-2.5 text-[18px] font-bold tracking-[-0.01em]">
      <Mark />
      TravelPilot
    </span>
  )
}
