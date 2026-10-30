import { PHOTOS } from '../../../lib/photos'

/** The page's one rest: Hội An's river front at full width, drifting slowly while it is on screen. */
export function PhotoBand() {
  const photo = PHOTOS.hoianRiver
  return (
    <figure aria-labelledby="band-caption">
      <div className="h-[72svh] min-h-[360px] overflow-hidden bg-sand">
        <img
          src={photo.src}
          width={photo.width}
          height={photo.height}
          alt={photo.alt}
          decoding="async"
          className="drift h-full w-full object-cover"
        />
      </div>
      <figcaption
        id="band-caption"
        className="flex justify-end px-[var(--margin)] pt-3 text-[14px] text-muted"
      >
        {photo.place}, một trong 34 địa điểm Đà Nẵng mà TravelPilot biết
      </figcaption>
    </figure>
  )
}
