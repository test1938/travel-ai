import 'leaflet/dist/leaflet.css'
import type { LatLngBoundsExpression, LatLngTuple } from 'leaflet'
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip } from 'react-leaflet'
import type { DayPlan } from '../../types/api'
import { mapStops } from './mapStops'

/**
 * Small map of one day: numbered stops in visiting order joined by a line. Circle markers need no image assets
 * (Leaflet's default marker icons break with bundlers). Tiles and attribution: OpenStreetMap (ODbL).
 */
export default function DayMap({ day }: { day: DayPlan }) {
  const stops = mapStops(day)
  const points: LatLngTuple[] = stops.map((s) => [s.lat, s.lon])
  const bounds: LatLngBoundsExpression = points
  return (
    <MapContainer
      bounds={bounds}
      boundsOptions={{ padding: [24, 24], maxZoom: 15 }}
      scrollWheelZoom={false}
      className="h-48 w-full rounded-tp"
      aria-label={`Bản đồ ngày ${day.dayIndex}`}
    >
      <TileLayer
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        maxZoom={19}
      />
      {points.length > 1 && (
        <Polyline
          positions={points}
          pathOptions={{ color: '#212529', weight: 2, dashArray: '4 4' }}
        />
      )}
      {stops.map((s) => (
        <CircleMarker
          key={`${s.order}-${s.name}`}
          center={[s.lat, s.lon]}
          radius={9}
          pathOptions={{
            color: '#212529',
            weight: 2,
            fillColor: s.food ? '#ffffff' : '#ffc20e',
            fillOpacity: 1,
          }}
        >
          <Tooltip direction="top" offset={[0, -8]}>
            {s.order}. {s.name}
          </Tooltip>
        </CircleMarker>
      ))}
    </MapContainer>
  )
}
