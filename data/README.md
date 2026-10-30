# TravelPilot dataset v1.0 — dữ liệu cuối cho app

Thư mục này là **đầu ra** của pipeline; app Spring chỉ cần đọc thư mục này. Không sửa tay ở đây: sửa ở `data_manual/` hoặc `knowledge/manual/` rồi chạy lại (xem cuối file).

## Các file

| File | Dùng cho | Nạp vào |
|---|---|---|
| `catalog_<dest>.json` | Danh sách địa điểm đã chọn lọc (`curated: true`), có mô tả. Planner chỉ chọn trong danh sách này. | bảng `place` (SeedLoader) |
| `places_pool_<dest>.json` | Toàn bộ ứng viên OSM đã làm giàu, bỏ bản trùng. Dùng cho tool `searchPlaces` khi người dùng hỏi điểm ngoài danh sách; **không** đưa vào planner mặc định. | bảng `place` với `curated=false`, hoặc bỏ qua ở v1 |
| `knowledge_<dest>.jsonl` | Chunk cho RAG, mỗi dòng 1 chunk (GUIDE / PLACE / TIPS). | pgvector (`vector_store`), `metadata` = mọi trường trừ `text` |
| `verification_queue_curated_<dest>.csv` | Những trường của địa điểm đã chọn vẫn còn là ước tính. | không nạp; dành cho người xác minh |
| `STATS.md` | Thống kê: số lượng, tỷ lệ đã xác minh, quota. | — |
| `schema/place_catalog.schema.json` | JSON Schema của `PlaceCatalog` (sinh từ pydantic). Dùng để viết entity/DTO Java và test. | — |
| `../knowledge/golden_set.json` | 24 câu hỏi kèm chunk mong đợi để đo retrieval (hit@k). | test của app |

`<dest>` ∈ `hanoi`, `danang`, `quangninh`.

## PlaceCatalog — các trường

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `id` | string | `<dest>-<osmType>-<osmId>` (ví dụ `hanoi-way-37625751`) hoặc `<dest>-manual-<slug>` cho điểm thêm tay. Ổn định giữa các lần chạy. |
| `destination` | enum | `HANOI`, `DANANG`, `QUANGNINH` |
| `type` | enum | `ACTIVITY`, `FOOD` |
| `name`, `nameEn`, `description` | string | `description` 1–2 câu tiếng Việt, không chứa giá |
| `curated` | bool | `true` trong `catalog_*`; trong pool chỉ có ở điểm đã chọn |
| `osmId`, `sourceUrl` | string | link OSM |
| `tags` | enum[] | xem `PlaceTag`. **Chú ý giá trị có gạch ngang:** `THEME-PARK`, `CABLE-CAR`, `STREET-FOOD` |
| `estCostVnd`, `childCostVnd` | int | giá vé/người (ACTIVITY) hoặc chi phí một bữa/người (FOOD). 0 = miễn phí vào cửa |
| `priceLevel` | enum | `LOW` (<100k, gồm cả miễn phí), `MID` (<400k), `HIGH` (<1tr), `PREMIUM` |
| `priceNote` | string | giải thích giá (nguồn, đã xác minh hay ước tính) — hiển thị cho người dùng |
| `priceSourceUrl`, `verifyAt` | string, date | chỉ có khi giá có nguồn (OSM `fee` hoặc xác minh tay) |
| `durationMin` | int | thời gian tham quan đề xuất |
| `childMinAge` | int | tuổi tối thiểu (0 = không giới hạn). Nếu nằm trong `reviewFields` thì **chưa được xác nhận**, không dùng làm cam kết an toàn |
| `indoor` | bool | dùng cho phương án ngày mưa |
| `bestTime` | enum | `MORNING`, `AFTERNOON`, `EVENING`, `ANY` |
| `openingHours` | string | cú pháp OSM `opening_hours` (ví dụ `Tu-Su 08:00-17:00`) |
| `lat`, `lon` | float | WGS84 |
| `popularity` | float 0–1 | proxy độ nổi tiếng, chỉ để xếp hạng |
| `dayTrip` | bool | cách tâm điểm đến > 20 km |
| `estimatedFields` | string[] | các trường là ước tính → UI hiển thị "ước tính" |
| `reviewFields` | string[] | các trường ước tính có rủi ro (giá vé, tuổi, trong nhà) → ưu tiên xác minh |
| `fieldSources` | map | nguồn của từng trường |

## Knowledge chunk (JSONL)

```json
{"docId": "hanoi-tips-weather-02", "destination": "HANOI", "docType": "TIPS", "lang": "vi",
 "title": "Hà Nội – Thời tiết… › Mùa thu…", "text": "...", "placeIds": [],
 "source": "...", "sourceUrl": "...", "license": "CC BY-SA 4.0", "retrievedAt": "2026-10-12"}
```

- `GUIDE`: Wikivoyage, cắt theo mục, 400–2500 ký tự. Đã bỏ mục khách sạn, liên lạc, "go next".
- **Mọi chunk đều là tiếng Việt.** Chunk từ Wikivoyage tiếng Anh được dịch (cache ở `knowledge/translations/<dest>.json`), giữ nguyên `docId`, có thêm `translatedFrom: "en"` và `originalTitle`. Nếu Wikivoyage đổi nội dung, `build_knowledge.py` báo chunk nào chưa có bản dịch hợp lệ (so `sourceHash`) và giữ tạm bản tiếng Anh.
- `PLACE`: một chunk cho mỗi địa điểm curated, `placeIds` = `[id]`. Gồm mô tả + đoạn mở đầu Wikipedia nếu có. **Không có giá, giờ mở cửa** — các số này luôn lấy từ catalog để tránh hai nguồn mâu thuẫn.
- `TIPS`: viết tay (`knowledge/manual/<dest>/*.md`), mỗi mục `##` là một chunk: thời tiết, đi lại, trẻ em, ngày mưa, ẩm thực, an toàn; Quảng Ninh có thêm hướng dẫn đi tàu vịnh.
- Khi embed nên ghép `title + "\n" + text`. Filter theo `destination` trước khi tìm.

## Gợi ý nạp vào Spring Boot

- **SeedLoader** (`ApplicationRunner`, chạy khi bảng rỗng): đọc `catalog_*.json` bằng Jackson → `PlaceEntity`. `tags` lưu `text[]` hoặc bảng phụ; `estimatedFields`, `reviewFields` lưu `text[]`; `fieldSources` lưu `jsonb`.
- **Enum có gạch ngang**: trong Java khai báo `THEME_PARK("THEME-PARK")` với `@JsonValue` trả về giá trị chuỗi, hoặc `@JsonProperty("THEME-PARK")` trên hằng số.
- **Ingestion RAG**: mỗi dòng JSONL → `new Document(docId, title + "\n" + text, metadata)`; không cần cắt thêm (chunk đã đủ nhỏ). Dùng `docId` làm id để chạy lại không bị trùng.
- **Hiển thị ước tính**: nếu `"estCostVnd" in estimatedFields` → hiện "~" và `priceNote`; nếu có `verifyAt` → hiện "giá cập nhật ngày …".
- **Budget**: cộng `estCostVnd × người lớn + childCostVnd × trẻ em` cho ACTIVITY và FOOD; không để LLM tự tính.

## Chạy lại từ đầu

```powershell
$env:TP_USE_CURL=1                      # chỉ khi mạng công ty lỗi SSL
python tools/fetch_overpass.py          # tải OSM (có cache)
python main.py                          # làm giàu + thêm tay + xác minh + chọn lọc -> data_final/catalog_*, places_pool_*
python tools/fetch_knowledge.py         # tải Wikivoyage, Wikipedia (có cache)
python tools/build_knowledge.py         # -> data_final/knowledge_*.jsonl
python tools/report.py                  # -> STATS.md, schema
python -m unittest discover -s tests    # kiểm tra tính nhất quán
```
