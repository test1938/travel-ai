# Hợp đồng dữ liệu (dataset v1.0, build ngày 2026-10-12)

**Nguồn dữ liệu:**
- Mọi file nằm trong thư mục `data/` ở gốc repo. Thư mục này **chỉ đọc**.
- Dữ liệu được sinh bởi một pipeline Python riêng (`travel-ai-data`). Muốn sửa thì sửa ở pipeline, chạy lại, rồi copy đè các file vào `data/`.

## 1. File trong `data/` và cách app dùng

| File | Đọc trong code bằng | Dùng cho |
|---|---|---|
| `catalog_{hanoi,danang,quangninh}.json` | `classpath:seed/catalog_*.json` | SeedLoader → bảng `places` (P0) |
| `knowledge_{hanoi,danang,quangninh}.jsonl` | `classpath:seed/knowledge_*.jsonl` | IngestionService → `vector_store` (P0) |
| `golden_set.json` | `classpath:eval/golden_set.json` (test resource) | RetrievalEvalRunner (hit@k, MRR) |
| `schema/place_catalog.schema.json` | (đọc bằng mắt; có thể dùng trong test validate JSON) | Tham chiếu khi viết entity/DTO |
| `places_pool_{hanoi,danang,quangninh}.json` | `classpath:seed/places_pool_*.json` (chỉ khi làm P1) | Tool `searchPlaces` (P1) |
| `README.md`, `STATS.md`, `ATTRIBUTION.md` | — | Tài liệu dữ liệu, README dự án, footer UI |

**Cách map `data/` vào classpath** (làm ở T0.03, trong `be/pom.xml`):

```xml
<build>
  <resources>
    <resource><directory>src/main/resources</directory></resource>
    <resource>
      <directory>../data</directory>
      <targetPath>seed</targetPath>
      <includes><include>catalog_*.json</include><include>knowledge_*.jsonl</include></includes>
    </resource>
  </resources>
  <testResources>
    <testResource><directory>src/test/resources</directory></testResource>
    <testResource>
      <directory>../data</directory>
      <targetPath>eval</targetPath>
      <includes><include>golden_set.json</include></includes>
    </testResource>
  </testResources>
</build>
```

- Khi làm P1 `searchPlaces`, thêm `<include>places_pool_*.json</include>`.
- Docker build backend phải dùng **gốc repo** làm build context để thấy được `../data` (xem T9.04).

## 2. Số lượng

| Dest | Catalog | ACTIVITY | FOOD | Trong nhà | Buổi tối | Miễn phí | Day trip | Chunks GUIDE/PLACE/TIPS |
|---|---|---|---|---|---|---|---|---|
| HANOI | 45 | 34 | 11 | 16 | 5 | 14 | 2 (Chùa Hương, Đường Lâm) | 88/45/33 = 166 |
| DANANG | 34 | 24 | 10 | 8 | 6 | 16 | 3 (Bà Nà, Hội An, Rừng dừa Bảy Mẫu) | 96/34/32 = 162 |
| QUANGNINH | 22 | 12 | 10 | 3 | 1 | 6 | 2 (Yên Tử, Quan Lạn) | 30/22/21 = 73 |

Quảng Ninh có ít dữ liệu. Với chuyến 4–5 ngày ở đây, bộ lọc phải nới lỏng có kiểm soát và trả về cảnh báo, không được trả về rỗng hoặc lặp lại hoạt động.

## 3. PlaceCatalog: các trường (mỗi phần tử trong `catalog_*.json`)

Bắt buộc theo schema: `id`, `destination`, `type`, `name`.

Trong catalog curated, **mọi trường dưới đây đều có giá trị (không null)**, trừ `nameEn`, `priceSourceUrl` và `verifyAt`.

| Trường | Kiểu JSON | Kiểu Java gợi ý | Ý nghĩa |
|---|---|---|---|
| `id` | string | `String` (PK) | `<dest>-<osmType>-<osmId>` (vd `danang-way-359114064`) hoặc `<dest>-manual-<slug>`. Ổn định giữa các lần build. |
| `destination` | enum | `Destination` | `HANOI` \| `DANANG` \| `QUANGNINH` |
| `type` | enum | `PlaceType` | `ACTIVITY` \| `FOOD` |
| `name` | string | `String` | Tên tiếng Việt để hiển thị |
| `nameEn` | string? | `String` | Tên tiếng Anh, có thể null |
| `description` | string | `String` | 1–2 câu tiếng Việt, không chứa giá |
| `curated` | bool | `boolean` | `true` với mọi phần tử trong catalog |
| `osmId` | string | `String` | `node/123`, `way/…`, `relation/…`; điểm thêm tay có thể không có |
| `tags` | enum[] | `Set<PlaceTag>` | Xem §4 |
| `estCostVnd` | int | `long` | ACTIVITY: giá vé người lớn. FOOD: chi phí **một bữa cho một người lớn**. `0` nghĩa là miễn phí vào cửa. |
| `childCostVnd` | int | `long` | Giá cho trẻ em, theo cùng quy ước. `0` nghĩa là miễn phí cho trẻ. |
| `priceLevel` | enum | `PriceLevel` | `LOW` (<100k, gồm miễn phí), `MID` (<400k), `HIGH` (<1tr), `PREMIUM` |
| `priceNote` | string | `String` | Giải thích nguồn giá: đã xác minh ngày nào, hay là ước tính. **Luôn hiển thị** cho người dùng (tooltip/popover). |
| `priceSourceUrl` | string? | `String` | Chỉ có khi giá có nguồn (OSM `fee` hoặc xác minh tay) |
| `verifyAt` | date? | `LocalDate` | Ngày xác minh giá. UI hiện "Giá cập nhật dd/MM/yyyy". Quá 180 ngày thì hiện thêm cảnh báo giá cũ. |
| `durationMin` | int | `int` | Thời gian tham quan đề xuất, từ 20 đến 480 phút |
| `childMinAge` | int | `int` | Tuổi tối thiểu, `0` là không giới hạn. Ví dụ: The Craftsman bar 18, Onsen 12, Yên Tử 6, Nhà hát Lớn 6. |
| `indoor` | bool | `boolean` | Dùng cho phương án ngày mưa |
| `bestTime` | enum | `BestTime` | `MORNING` \| `AFTERNOON` \| `EVENING` \| `ANY` |
| `openingHours` | string | `String` | Cú pháp OSM `opening_hours`, xem §6 |
| `lat`, `lon` | float | `double` | WGS84 |
| `popularity` | float 0–1 | `double` | Proxy độ nổi tiếng, chỉ dùng để xếp hạng |
| `dayTrip` | bool | `boolean` | Cách tâm điểm đến hơn 20 km. Thường chiếm nguyên ngày. |
| `sourceUrl` | string | `String` | Link OSM |
| `estimatedFields` | string[] | `List<String>` (`text[]`) | Các trường là ước tính, UI hiện "~" |
| `reviewFields` | string[] | `List<String>` (`text[]`) | Các trường ước tính có rủi ro (giá, tuổi, trong nhà). Ví dụ `childMinAge` nằm ở đây thì **không được dùng làm cam kết an toàn** trong câu trả lời. |
| `fieldSources` | object | `Map<String,String>` (`jsonb`) | Nguồn của từng trường, dùng để debug và trả lời câu hỏi "số này lấy từ đâu" |

Pool (`places_pool_*.json`) dùng cùng schema, nhưng nhiều trường có thể null và `curated` có thể vắng.

## 4. Enum, phải khớp chính xác chuỗi JSON

```
Destination : HANOI, DANANG, QUANGNINH
PlaceType   : ACTIVITY, FOOD
BestTime    : MORNING, AFTERNOON, EVENING, ANY
PriceLevel  : LOW, MID, HIGH, PREMIUM
PlaceTag    : BEACH, NATURE, MOUNTAIN, ISLAND, CAVE, RIVER, WATER, CULTURE, HISTORY, MUSEUM, TEMPLE, ART,
              LANDMARK, VILLAGE, CRAFT, FAMILY, KIDS, THEME-PARK, CABLE-CAR, SHOW, FOOD, SEAFOOD, STREET-FOOD,
              LOCAL, CAFE, MARKET, SHOPPING, NIGHTLIFE, NIGHT, PHOTO, ROMANTIC, RELAX, SPA, SIGHTSEEING,
              ADVENTURE, HIKING, KAYAK, BOAT
```

- **Tag có gạch ngang:** trong Java, đặt tên hằng `THEME_PARK`, `CABLE_CAR`, `STREET_FOOD` và gắn `@JsonProperty("THEME-PARK")`. Mẫu ở `05-java-snippets.md`.
  - Khi lưu xuống DB (`text[]`), dùng **chuỗi JSON** (có gạch ngang), để SQL `'THEME-PARK' = ANY(tags)` khớp với dữ liệu gốc.
- **Không bịa tag mới.** Muốn thêm thì sửa ở pipeline Python trước.

**Tâm điểm đến** (dùng cho distanceFit và gọi Open-Meteo), timezone `Asia/Ho_Chi_Minh`:

| Destination | lat | lon |
|---|---|---|
| HANOI | 21.0285 | 105.8542 |
| DANANG | 16.0544 | 108.2208 |
| QUANGNINH | 20.9590 | 107.0450 |

## 5. Quy tắc nghiệp vụ suy ra từ dữ liệu

- **Chi phí của một mục:**
  - Công thức: `estCostVnd × adults + childCostVnd × children`. Trong đó `children` là số trẻ có `age ≥ childMinAge`; HardFilter đã loại điểm không hợp tuổi.
  - Áp dụng cho cả ACTIVITY và FOOD.
- **Phạm vi ngân sách:** chỉ gồm **vé tham quan và các bữa ăn có trong lịch**, không gồm khách sạn, di chuyển hay ăn sáng ngoài lịch.
  - UI phải ghi rõ điều này. Form đặt nhãn "Ngân sách vui chơi và ăn uống".
- **Mục giá 0 và estimated:** khi `estCostVnd == 0` và `"estCostVnd"` có trong `estimatedFields`, hiển thị "Miễn phí (ước tính)", không hiển thị "0đ".
- **Day trip** (`dayTrip = true`, hoặc `durationMin > 210`):
  - Đặt ở slot MORNING. Buổi chiều cùng ngày không thêm ACTIVITY, chỉ được thêm FOOD.
  - Vẫn được thêm hoạt động buổi tối nếu pace cho phép.
- **Gộp vịnh Hạ Long:**
  - Các hang, đảo trong vịnh (Sửng Sốt, Ti Tốp, Thiên Cung, Luồn) **đã gộp** vào một điểm "Du thuyền Vịnh Hạ Long" (`quangninh-node-2707025319`) để không tính tiền trùng.
  - Đừng thêm các hang này từ pool vào planner.
- **Điểm đã bị loại vì đóng cửa hoặc cấm** (chỉ còn trong pool, **không bao giờ** đưa vào planner):
  - Asia Park.
  - Helio Center (lưu ý: "Chợ đêm Helio" vẫn còn trong catalog).
  - Phố đường tàu Hà Nội.
  - Núi Bài Thơ.
  - Sun World Hạ Long – công viên Rồng.
- **Lăng Chủ tịch Hồ Chí Minh** (`hanoi-way-37625751`) đóng cửa từ 2026-09-04 đến 2026-11-02 (nằm trong `openingHours`), nên HardFilter phải loại theo ngày đi.
- **Ăn uống:** mỗi điểm đến chỉ có 10–11 quán.
  - Kế hoạch gợi ý **2 bữa mỗi ngày** (trưa, tối), không xếp bữa sáng.
  - Nếu sau khi lọc không đủ quán, cho phép lặp FOOD kèm warning `FOOD_REPEATED`. ACTIVITY thì không bao giờ được lặp.

## 6. openingHours: các mẫu có trong catalog

Bộ kiểm tra giờ mở cửa phải xử lý được **mọi chuỗi** dưới đây. Viết test duyệt toàn bộ catalog và assert không ném exception.

```
24/7
sunrise-sunset
08:00-17:00                                   (không có ngày = mọi ngày)
Mo-Su 08:00-17:00
Tu-Su 08:00-12:00,13:30-17:00                 (nhiều khoảng giờ)
Mo-Th 06:00-18:00; Fr-Su 06:00-22:30          (nhiều rule, ngăn bởi ;)
Tu-Th,Sa,Su 08:00-12:00,14:00-16:30           (danh sách ngày)
Mo-Su 19:00-24:00 | Mo-Su 18:00-02:00         (24:00, qua nửa đêm)
Mo-Su 08:00-11:00,13:00-16:00; Fr off         (off)
Mo-Fr 10:00-22:00; Sa,Su,PH 09:30-22:00       (PH = ngày lễ → coi như Sa/Su)
Mo-Su 11:00-14:00, Mo-Su 17:30-21:30          (rule ngăn bởi ", ")
Mar-Sep 07:30-18:30; Oct-Feb 07:30-17:30      (khoảng tháng)
Apr 16-Oct 31 07:30-17:30; Nov 01-Apr 15 07:30-17:00   (khoảng ngày-tháng)
Apr-Oct Tu-Th 07:30-10:30; ... ; 2026 Sep 04-2026 Nov 02 off   (khoảng ngày có năm + off)
```

- **Ngữ nghĩa:** rule đứng sau ghi đè rule đứng trước nếu cùng ngày (giống OSM).
- **Chuỗi không parse được:** coi như "không rõ". Không loại điểm, nhưng thêm warning `HOURS_UNKNOWN` và log ở mức WARN.
- **Có thể thử thư viện** OpeningHoursParser của Simon Poole (kiểm tra Maven coordinates trước khi dùng). Nếu không hợp, tự viết parser nhỏ cho đúng các mẫu trên, có test cho từng mẫu.

## 7. Knowledge chunk (`knowledge_*.jsonl`, mỗi dòng một JSON)

```json
{"docId": "danang-guide-en-da-nang-intro-01", "destination": "DANANG", "docType": "GUIDE", "lang": "vi",
 "title": "Đà Nẵng", "text": "...", "placeIds": [],
 "source": "Wikivoyage (en) — Da Nang (revision 5383180), dịch sang tiếng Việt",
 "sourceUrl": "https://en.wikivoyage.org/wiki/Da_Nang", "license": "CC BY-SA 4.0 (bản dịch)",
 "retrievedAt": "2026-10-12", "translatedFrom": "en", "originalTitle": "Da Nang"}
```

| Trường | Ghi chú |
|---|---|
| `docId` | Duy nhất, ổn định. Dạng `<dest>-guide-…`, `<dest>-place-<placeId>`, `<dest>-tips-<file>-NN`. |
| `docType` | `GUIDE` (Wikivoyage), `PLACE` (mỗi điểm curated một chunk), `TIPS` (viết tay: thời tiết, đi lại, trẻ em, ngày mưa, ẩm thực, an toàn, đi tàu vịnh) |
| `lang` | Luôn là `vi` |
| `title` | Breadcrumb, ví dụ `"Hà Nội – Thời tiết › Mùa thu"` |
| `text` | 80–2500 ký tự, **không cần cắt thêm** |
| `placeIds` | PLACE: đúng 1 id. TIPS: có thể liệt kê vài id. GUIDE: rỗng. |
| `source`, `sourceUrl`, `license`, `retrievedAt` | Hiển thị trong popover nguồn. `sourceUrl` của PLACE có thể chứa nhiều URL, ngăn bởi dấu cách. |
| `translatedFrom`, `originalTitle` | Chỉ có ở chunk dịch từ tiếng Anh |

**Cách ingest** (đã chốt, không dùng TokenTextSplitter):

1. Mỗi dòng tạo thành một `Document`:
   - id: `UUID.nameUUIDFromBytes(docId.getBytes(UTF_8)).toString()`. PgVectorStore dùng cột `uuid`, và id tất định giúp upsert được.
   - content: `title + "\n" + text`.
2. Metadata là mọi trường trừ `text`, **bỏ các giá trị null**, cộng thêm:
   - `placeId`: chuỗi, bằng `placeIds[0]` nếu `placeIds.size() == 1`. Filter expression của Spring AI lọc scalar dễ hơn lọc list.
   - `placeIds`: giữ dạng list.
3. Trước khi add, **xóa theo `destination`** rồi add lại. Mục đích là không để sót chunk cũ khi số chunk giảm. Chạy 2 lần thì `count(*)` không đổi.
4. Retriever **luôn filter `destination == '<ENUM>'`**. Biểu thức lọc được dựng từ enum, không nối chuỗi từ input người dùng.

**Câu trả lời trích nguồn:** hiển thị `title`, `source`, `sourceUrl`, `license`. Chunk dịch có nhãn "(bản dịch)" trong `license`.

## 8. Golden set (`golden_set.json`): 24 câu, 8 câu cho mỗi điểm đến

```json
{"id": "dn-03", "destination": "DANANG", "question": "Đi Bà Nà cần chuẩn bị gì?",
 "expectedDocIds": ["danang-place-danang-way-359114064", "danang-tips-weather-05"],
 "expectedPlaceIds": ["danang-way-359114064"], "mustMention": ["áo khoác"]}
```

- **hit@k:** có ít nhất một `expectedDocIds` (so với metadata `docId`) nằm trong top-k.
- **MRR:** nghịch đảo thứ hạng của doc đúng đầu tiên.
- `mustMention` dùng để chấm câu trả lời của `/ask`: câu trả lời nên chứa cụm này (không phân biệt hoa thường). Đây là check mềm, chỉ báo cáo.
- **Mục tiêu:** hit@5 ≥ 80%. Ghi số thật vào `be/eval/results/`, kể cả khi không đạt.

## 9. Ghi công (bắt buộc ở footer UI và README)

> Dữ liệu bản đồ © OpenStreetMap contributors (ODbL). Nội dung hướng dẫn từ Wikivoyage và Wikipedia (CC BY-SA 4.0). Giá vé có ngày cập nhật, có thể thay đổi.

## 10. Điểm dữ liệu còn yếu (không chặn code, nên biết)

- **Còn trong `reviewFields`** (chưa xác minh):
  - Giá vé của Bảo tàng Phòng không – Không quân và Cung Quy hoạch Quảng Ninh.
  - `childMinAge` của Bà Nà, cáp treo Nữ Hoàng, Yên Tử, Công viên Tuần Châu.
  - Giá trẻ em của Onsen.
  - Giá trẻ em Nhà hát Lớn đang là 400.000đ, cao hơn giá người lớn: số này chưa xác minh, không dựa vào đó để kết luận.
- **Giá quán ăn:** đều là ước tính theo loại món.
- **Lỗi đã biết trong chunk dịch Chùa Cầu (Hội An):** chunk ghi "tượng heo", trong khi tượng thực tế là khỉ và chó. Nếu golden test liên quan thì bỏ qua lỗi này.
- **Tỉ lệ giá có nguồn:** khoảng 75% hoạt động ở Hà Nội và Đà Nẵng, 66% ở Quảng Ninh (xem `data/STATS.md`).
