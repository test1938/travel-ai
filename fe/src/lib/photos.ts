import type { Destination } from '../types/api'

/**
 * Photographs of the three destinations, from Wikimedia Commons (resized to 1600px; sources and licences in
 * public/photos/credits.json). They show the places, never a TravelPilot customer or product.
 */
export interface Photo {
  src: string
  width: number
  height: number
  alt: string
  /** What the picture shows, as a caption. */
  place: string
  credit: string
  page: string
}

export const PHOTOS = {
  hanoi: {
    src: '/photos/hanoi-hoan-kiem.jpg',
    width: 1600,
    height: 1067,
    alt: 'Mặt hồ Hoàn Kiếm buổi chiều, một người ngồi tựa gốc cây nhìn ra hồ',
    place: 'Hồ Hoàn Kiếm, Hà Nội',
    credit: 'Vyacheslav Argenberg, CC BY 4.0',
    page: 'https://commons.wikimedia.org/wiki/File:Hanoi,_Vietnam,_Hoan_Kiem_Lake.jpg',
  },
  hoianRiver: {
    src: '/photos/hoian-river.jpg',
    width: 1600,
    height: 931,
    alt: 'Dãy nhà vàng mái ngói của phố cổ Hội An soi bóng xuống sông Thu Bồn',
    place: 'Phố cổ Hội An bên sông Thu Bồn',
    credit: 'Steffen Schmitz, CC BY-SA 4.0',
    page: 'https://commons.wikimedia.org/wiki/File:H%E1%BB%99i_An,_Ancient_Town,_2020-01_CN-01.jpg',
  },
  hoianStreet: {
    src: '/photos/hoian-street.jpg',
    width: 1600,
    height: 1048,
    alt: 'Phố Trần Phú, Hội An: đèn lồng giăng ngang đường, tường vàng và hàng vải hai bên',
    place: 'Phố Trần Phú, Hội An',
    credit: 'Steffen Schmitz, CC BY-SA 4.0',
    page: 'https://commons.wikimedia.org/wiki/File:H%E1%BB%99i_An,_Ancient_Town,_2020-01_CN-10.jpg',
  },
  bana: {
    src: '/photos/bana-mist.jpg',
    width: 1600,
    height: 1067,
    alt: 'Lâu đài trên đỉnh Bà Nà nhìn từ trên cao, chìm trong biển mây',
    place: 'Đỉnh Bà Nà trong sương, Đà Nẵng',
    credit: 'Vivu Vietnam, CC BY-SA 4.0',
    page: 'https://commons.wikimedia.org/wiki/File:Ba_Na_Hills_in_the_mist.jpg',
  },
  danang: {
    src: '/photos/danang-my-khe.jpg',
    width: 1600,
    height: 1200,
    alt: 'Bãi biển Mỹ Khê nhìn từ trên cao: cát trắng, hàng dừa và đường ven biển',
    place: 'Bãi biển Mỹ Khê, Đà Nẵng',
    credit: 'Jpatokal, CC BY-SA 4.0',
    page: 'https://commons.wikimedia.org/wiki/File:My_Khe_Beach_Danang_Coastline.jpg',
  },
  halong: {
    src: '/photos/halong-bay.jpg',
    width: 1600,
    height: 1068,
    alt: 'Du thuyền trắng giữa các đảo đá vôi trên vịnh Hạ Long',
    place: 'Vịnh Hạ Long nhìn từ đảo Ti Tốp',
    credit: 'Jakub Hałun, CC BY 4.0',
    page: 'https://commons.wikimedia.org/wiki/File:View_of_sea_from_Titov_Island,_Ha_Long_Bay,_Vietnam,_20240128_1337_3732.jpg',
  },
} satisfies Record<string, Photo>

/** The picture shown for the destination the traveler picked. */
export const DESTINATION_PHOTO: Record<Destination, Photo> = {
  HANOI: PHOTOS.hanoi,
  DANANG: PHOTOS.danang,
  QUANGNINH: PHOTOS.halong,
}

/** Credit line for the footer, in page order. */
export const PHOTO_CREDITS: Photo[] = [
  PHOTOS.hanoi,
  PHOTOS.hoianStreet,
  PHOTOS.bana,
  PHOTOS.halong,
  PHOTOS.danang,
  PHOTOS.hoianRiver,
]
