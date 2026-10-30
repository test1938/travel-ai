/**
 * Types mirroring the backend DTOs (be/src/main/java/dev/travelpilot). Keep in sync by hand until an OpenAPI
 * generator is added. Enums are string unions with the exact JSON values.
 */

/** Error codes returned by the backend in ProblemDetail.code (be: common/error/ErrorCode.java). */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'INPUT_TOO_LONG'
  | 'NOT_FOUND'
  | 'RATE_LIMITED'
  | 'LLM_ERROR'
  | 'LLM_TIMEOUT'
  | 'PLAN_INVALID_AFTER_REPAIR'
  | 'INTERNAL'

/** RFC 9457 ProblemDetail as produced by GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  code?: ErrorCode
  requestId?: string
  errors?: Record<string, string>
}

// ---- domain enums ----

export type Destination = 'HANOI' | 'DANANG' | 'QUANGNINH'
export type PlaceType = 'ACTIVITY' | 'FOOD'
export type Slot = 'MORNING' | 'AFTERNOON' | 'EVENING'
export type BestTime = Slot | 'ANY'
export type Pace = 'RELAXED' | 'NORMAL' | 'PACKED'

/** Dataset tag codes; note the hyphenated ones. */
export type PlaceTag =
  | 'BEACH'
  | 'NATURE'
  | 'MOUNTAIN'
  | 'ISLAND'
  | 'CAVE'
  | 'RIVER'
  | 'WATER'
  | 'CULTURE'
  | 'HISTORY'
  | 'MUSEUM'
  | 'TEMPLE'
  | 'ART'
  | 'LANDMARK'
  | 'VILLAGE'
  | 'CRAFT'
  | 'FAMILY'
  | 'KIDS'
  | 'THEME-PARK'
  | 'CABLE-CAR'
  | 'SHOW'
  | 'FOOD'
  | 'SEAFOOD'
  | 'STREET-FOOD'
  | 'LOCAL'
  | 'CAFE'
  | 'MARKET'
  | 'SHOPPING'
  | 'NIGHTLIFE'
  | 'NIGHT'
  | 'PHOTO'
  | 'ROMANTIC'
  | 'RELAX'
  | 'SPA'
  | 'SIGHTSEEING'
  | 'ADVENTURE'
  | 'HIKING'
  | 'KAYAK'
  | 'BOAT'

/** Tags offered on the form (be: TripRequest.ALLOWED_PREFERENCES). */
export const PREFERENCES = [
  'BEACH',
  'NATURE',
  'CULTURE',
  'HISTORY',
  'MUSEUM',
  'FOOD',
  'STREET-FOOD',
  'SEAFOOD',
  'PHOTO',
  'FAMILY',
  'THEME-PARK',
  'NIGHT',
  'SHOPPING',
  'RELAX',
  'ADVENTURE',
] as const satisfies readonly PlaceTag[]

export type Preference = (typeof PREFERENCES)[number]

// ---- trip request ----

/** POST /api/v1/trips body. budgetVnd covers tickets and planned meals only. */
export interface TripRequest {
  destination: Destination
  /** ISO date yyyy-MM-dd */
  startDate: string
  days: number
  adults: number
  childrenAges: number[]
  budgetVnd: number
  preferences: Preference[]
  pace: Pace
  notes: string[]
}

// ---- trip plan ----

export interface ScoreBreakdown {
  total: number
  preference: number
  budget: number
  distance: number
  schedule: number
  group: number
  popularity: number
  rainBonus: number
}

export interface DayWeather {
  date: string
  rainy: boolean
  tempMinC: number | null
  tempMaxC: number | null
  weatherCode: number | null
  summary: string | null
}

export interface PlanItem {
  placeId: string
  name: string
  type: PlaceType
  slot: Slot
  /** HH:mm */
  start: string
  /** HH:mm */
  end: string
  durationMin: number
  /** Cost for the whole group in VND. */
  costVnd: number
  costEstimated: boolean
  priceNote: string | null
  /** ISO date of the last price check, if any. */
  verifyAt: string | null
  indoor: boolean
  bestTime: BestTime
  reason: string
  /** Evidence ids, e.g. "ev-2", matching TripPlan.sources[].id */
  sourceIds: string[]
  score: ScoreBreakdown | null
  lat: number
  lon: number
}

export interface DayPlan {
  dayIndex: number
  date: string
  weather: DayWeather | null
  items: PlanItem[]
}

export interface Budget {
  totalVnd: number
  limitVnd: number
  byType: Partial<Record<PlaceType, number>>
  overBudget: boolean
}

export interface Warning {
  code: string
  message: string
  dayIndex: number | null
  placeId: string | null
}

export interface Source {
  id: string
  docId: string
  title: string
  url: string | null
  license: string | null
  snippet: string
}

export interface PlanMeta {
  promptVersion: string | null
  model: string | null
  repaired: boolean
  repairs: number
  fallback: boolean
  latencyMs: number
}

export interface TripPlan {
  tripId: string
  version: number
  title: string
  summary: string
  days: DayPlan[]
  budget: Budget
  warnings: Warning[]
  sources: Source[]
  meta: PlanMeta
}

// ---- playground ----

export interface AiChatRequest {
  message: string
}

export interface AiChatResponse {
  answer: string
  model: string | null
  latencyMs: number
}

// ---- chat ----

export type Intent = 'ASK_QUESTION' | 'MODIFY_PLAN' | 'SMALL_TALK' | 'OUT_OF_SCOPE'

/** GET /api/v1/trips/{id}/messages item. */
export interface ChatMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  intent: Intent | null
  tripVersion: number | null
  createdAt: string
}

/** POST /api/v1/trips/{id}/messages response; plan/newVersion are set when the trip changed. */
export interface ChatReply {
  reply: string
  intent: Intent
  sources: Source[]
  newVersion: number | null
  plan: TripPlan | null
  changeSummary: string | null
}
