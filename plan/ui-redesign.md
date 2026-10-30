# UI redesign (2026-10-30)

Redesign of the React frontend with the **taste-skill** (`design-taste-frontend`, from
github.com/leonxlnx/taste-skill, installed in the user's Claude skills folder, not in this repo). Visual reference
from the owner: the sungroup.talent.vn style. That means:

- sun yellow `#ffc20e` on white, with ink `#212529`;
- Roboto;
- 3px corners;
- large photographs of resorts and landscapes.

Only the style was taken. The pages use no logo, name, photograph or text of Sun Group.

Stack, routes, API calls and behaviour are unchanged; frontend tests 42 → 44.

## Two kinds of screen

| Screen | Kind | Dealt | Seed |
|---|---|---|---|
| `/` (plan a trip) | page | direction **The sentence is the layout** (`sentence-as-layout`), palette and type kept from the brand | 1552521482 |
| `/trips/:id`, `/demo` | app screen | style **Achromatic ledger**, palette and type kept from the brand | 2083373864 |

**One deviation from the app sheet.** The sheet dealt an open-plane shell with a sidebar. TravelPilot has two
destinations, so the screen uses the "no sidebar" archetype from `app-screens.md` §3.1: a 64px bar over a centred
1152px column (744 + 24 + 384).

## The idea

**The idea.** On the home page, the traveller's request becomes the poster-scale sentence:

> Đi [Đà Nẵng] [3 ngày]. Khởi hành [ngày], nhóm có [2] người lớn [+ có trẻ em]. Ngân sách vui chơi và ăn uống
> [6.000.000]đ…

- Every blank is a real form control. The validation and the payload are the same as before.
- **The live thing** is the preview beside the sentence. It recomputes on every change and shows:
  - the destination's photograph;
  - the dates;
  - the money per day;
  - the most activities the pace allows;
  - the child-age filter.
- Three ready trips refill the sentence. They are the demo scenarios of the manual tests.

## Sections, in reading order

1. **Hero, "sentence split by a frame".**
   - "Lịch trình | [frame] | từng ngày".
   - Hà Nội, Hội An, Bà Nà and Hạ Long pass through the frame on a sticky stage that is pinned for about two screens.
   - On phones the destinations become a row you swipe.
2. **The trip sentence** with its live preview, or the planning progress or an error in the preview's place.
3. **"Mười lăm giây, năm bước".**
   - The real planning steps drawn as one bar, each segment as wide as the step takes.
   - The model's step is yellow.
4. **Full-bleed band of Hội An's river front**, drifting slowly. This is the page's one rest.
5. **"Mỗi điểm có lý do".** Day 1 of the sample plan, rendered by the same components as a real trip
   (`data-ui`).
6. **"101 địa điểm đã kiểm tra".** Real catalog names written as one sentence, with one name lit at a time.
7. **Footer in the night field.**
   - The invitation and the action.
   - Credits for every photograph and data source.
   - The name "TravelPilot" cropped by the bottom edge.

## Checks

| Check | Before | After |
|---|---|---|
| `inspect.mjs /` (1440/768/390) | 8 FAIL, 8 WARN | **0 FAIL**, 4 WARN |
| `inspect.mjs /demo --app` | 1 FAIL, 2 WARN | **0 FAIL**, 2 WARN |
| `lint.mjs` (page files / app files) | n/a | 0 FAIL / 0 FAIL, 1 WARN |
| Font sizes in use on `/` | 7 | 6 (14 / 16 / 18 / 34 / 66 / footer name) |

### Warnings kept, with the reason

- **`card-rows`.** The two rows are the preference chips and the pace options: form controls, not cards.
- **`no-footage`.** `assets.mjs --video` found no honest clip of the three destinations on Wikimedia Commons or
  the Internet Archive.
- **`lopsided` (figure).**
  - The full-bleed band is a full-width photograph. The meter counts only its right-aligned caption.
- **`side-scroll` (768/390).** The swipe row of destinations on phones, made on purpose.
- **App screen.**
  - **`font-fallback`:** Leaflet's stylesheet names a monospace face that is never used for text.
  - **`unused-canvas`:** the centred column of the no-sidebar shell.
  - **`shadow`:** the source popover floats, so it may carry the one shadow.

## Critique

```
ROUND 1
Seen in one second: "Lịch trình … từng ngày" around a lake photo; a yellow action.
Idea (one sentence): the trip request is the poster sentence, its blanks are the form.
Live thing: the preview beside the sentence (photo, dates, money per day, pace cap).
Layout words by section: hero=split title + reel / plan=sentence + preview / how=one bar / band=full photo /
  sample=statement + product fragment / places=names sentence / footer=invitation + cropped name
Scores: design 7 · usability 7 · creativity 8 · content 7 → overall 7.2
Caps applied: none
Top three problems:
1. Phone: the pinned title slid over the reel's pictures and the h1 had no box (display: contents)
   → phones get a static title on two lines and a swipe row.
2. The title read as covered (pointer-events: none over the frame) → removed.
3. The bone ground was read as yellow cream (#f5f3ee) → neutral #f3f3f1; type sizes cut from 12 to 6.

ROUND 2
Seen in one second: same, cleaner on phones; the planning sentence reads as a sentence.
Scores: design 7.5 · usability 8 · creativity 8 · content 7.5 → overall 7.7
Top three problems:
1. The hero frame holds 11% of the first screen; the page is quiet until the Hội An band (accepted: it
   is the blueprint's proportion).
2. The stage bar squeezed at 768px → horizontal only from 1024px.
3. The hero button sat close enough to the plan heading on phones to read as an eyebrow → 72px space.
```

What still falls short:

- The hero is calm rather than lush.
- A real clip of a destination would add the one moving picture the page lacks; today it has the slow drift
  of the band.

## Pictures and credits

| File | Shows | Creator, licence | Source |
|---|---|---|---|
| `hanoi-hoan-kiem.jpg` | Hồ Hoàn Kiếm, Hà Nội | Vyacheslav Argenberg, CC BY 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Hanoi,_Vietnam,_Hoan_Kiem_Lake.jpg) |
| `hoian-street.jpg` | Phố Trần Phú, Hội An | Steffen Schmitz, CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:H%E1%BB%99i_An,_Ancient_Town,_2020-01_CN-10.jpg) |
| `bana-mist.jpg` | Đỉnh Bà Nà trong sương | Vivu Vietnam, CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Ba_Na_Hills_in_the_mist.jpg) |
| `halong-bay.jpg` | Vịnh Hạ Long từ đảo Ti Tốp | Jakub Hałun, CC BY 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:View_of_sea_from_Titov_Island,_Ha_Long_Bay,_Vietnam,_20240128_1337_3732.jpg) |
| `danang-my-khe.jpg` | Bãi biển Mỹ Khê | Jpatokal, CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:My_Khe_Beach_Danang_Coastline.jpg) |
| `hoian-river.jpg` | Phố cổ Hội An bên sông Thu Bồn | Steffen Schmitz, CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:H%E1%BB%99i_An,_Ancient_Town,_2020-01_CN-01.jpg) |

- All six are resized to 1600px. The credits are in the home footer and in `fe/public/photos/credits.json`.
- None is presented as a customer's photo or as TravelPilot's own.

## Sample content to confirm

- **Timings on the stage bar.** "Nửa giây, tức thì, một giây, 10–15 giây, tức thì" are typical. Replace them with
  measured p50s after the first real-model eval (`tp.plan.latency{step}`).
- **The three ready trips** are the manual-test scenarios.

## Tools and fonts

- **Re-running the checks.** To run the skill's checks again, build, run `vite preview`, then:
  ```
  node <skill>/scripts/inspect.mjs http://localhost:4173/
  node <skill>/scripts/inspect.mjs http://localhost:4173/demo --app
  ```
  Behind the company proxy, the picture search (`assets.mjs`) needs `NODE_USE_ENV_PROXY=1`.
- **Fonts.** Roboto is self-hosted via `@fontsource/roboto` (OFL). The strict CSP in `fe/Caddyfile` stays
  unchanged.
