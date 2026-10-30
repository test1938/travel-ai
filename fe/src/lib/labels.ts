import type { Destination, Pace, PlaceTag, PlaceType, Slot } from '../types/api'

export const DESTINATION_LABELS: Record<Destination, string> = {
  HANOI: 'Hà Nội',
  DANANG: 'Đà Nẵng (gồm Hội An, Bà Nà)',
  QUANGNINH: 'Quảng Ninh (Hạ Long)',
}

export const PACE_LABELS: Record<Pace, { label: string; hint: string }> = {
  RELAXED: { label: 'Thư thả', hint: 'tối đa 2 hoạt động/ngày' },
  NORMAL: { label: 'Vừa phải', hint: 'tối đa 3 hoạt động/ngày' },
  PACKED: { label: 'Dày đặc', hint: 'tối đa 4 hoạt động/ngày' },
}

export const SLOT_LABELS: Record<Slot, string> = {
  MORNING: 'Sáng',
  AFTERNOON: 'Chiều',
  EVENING: 'Tối',
}

export const TYPE_LABELS: Record<PlaceType, string> = {
  ACTIVITY: 'Tham quan',
  FOOD: 'Ăn uống',
}

export const TAG_LABELS: Partial<Record<PlaceTag, string>> = {
  BEACH: 'Biển',
  NATURE: 'Thiên nhiên',
  CULTURE: 'Văn hóa',
  HISTORY: 'Lịch sử',
  MUSEUM: 'Bảo tàng',
  FOOD: 'Ẩm thực',
  'STREET-FOOD': 'Ăn vặt đường phố',
  SEAFOOD: 'Hải sản',
  PHOTO: 'Chụp ảnh',
  FAMILY: 'Gia đình',
  'THEME-PARK': 'Công viên giải trí',
  NIGHT: 'Đi chơi tối',
  SHOPPING: 'Mua sắm',
  RELAX: 'Thư giãn',
  ADVENTURE: 'Trải nghiệm',
}

/** Labels for the score components shown in "Vì sao xếp hạng cao". */
export const SCORE_LABELS = {
  preference: 'Hợp sở thích',
  budget: 'Hợp ngân sách',
  distance: 'Gần trung tâm',
  schedule: 'Hợp nhịp độ',
  group: 'Hợp nhóm khách',
  popularity: 'Độ nổi tiếng',
} as const
