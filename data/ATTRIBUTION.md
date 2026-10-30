# Nguồn dữ liệu và giấy phép

| Nguồn | Dùng cho | Giấy phép | Yêu cầu |
|---|---|---|---|
| OpenStreetMap (qua Overpass API) | Tên, tọa độ, loại địa điểm, giờ mở cửa, `fee`, website | ODbL 1.0 | Ghi "© OpenStreetMap contributors" và link https://www.openstreetmap.org/copyright ở nơi hiển thị bản đồ/dữ liệu. Cơ sở dữ liệu dẫn xuất (catalog) chia sẻ theo ODbL nếu công bố. |
| Wikivoyage (en, vi) | Chunk GUIDE trong `knowledge_*.jsonl`; bản tiếng Anh được dịch sang tiếng Việt | CC BY-SA 4.0 | Ghi nguồn + link trang (đã có ở `source`, `sourceUrl`); bản dịch là tác phẩm phái sinh, ghi rõ "dịch sang tiếng Việt" (trường `source`, `translatedFrom`) và chia sẻ cùng giấy phép. |
| Wikipedia (vi, en) qua Wikidata | Đoạn mở đầu trong chunk PLACE | CC BY-SA 4.0 | Như trên. |
| Trang chính thức của điểm tham quan, báo chí | Giá vé, giờ mở cửa đã xác minh (`priceSourceUrl`) | Chỉ trích dẫn sự kiện (giá, giờ) | Lưu URL và ngày xác minh (`verifyAt`). |
| Nội dung viết tay (`knowledge/manual`, mô tả trong `data_manual/curation_*.csv`) | Chunk TIPS, `description` | CC BY-SA 4.0 | — |

Gợi ý dòng ghi công trong UI: *"Dữ liệu bản đồ © OpenStreetMap contributors (ODbL). Nội dung hướng dẫn từ Wikivoyage và Wikipedia (CC BY-SA 4.0). Giá vé có ngày cập nhật, có thể thay đổi."*
