# Retro v0.1 (bản nháp, điền số thật sau khi chạy `plan/manual-test-v0.1.md`)

Ngày: 2026-10-30 · Phạm vi: Phase 0–3 (form → lịch trình có nguồn, ngân sách do backend tính).

## Số liệu (chờ chạy thật)

| Chỉ số | Mục tiêu | Thực tế |
|---|---|---|
| Thời gian tạo lịch (p50 / max, 3 kịch bản) | < 20 s | … |
| Bản nháp hợp lệ ngay lần đầu | ≥ 95% | … / 3 |
| Cần repair / phải fallback | thấp | … / … |
| Retrieval hit@5 (golden set) | ≥ 80% | … |
| Test tự động | xanh | backend: toàn bộ xanh (offline); frontend: 25 test |

## Giữ

- **LLM chỉ chọn ID, backend tính mọi con số.** Validator + `DaySchedule` dùng chung nên lịch được duyệt chính là lịch người dùng thấy.
- **Test không cần mạng:** stub chat/embedding, `SimpleVectorStore`, Testcontainers tự bỏ qua khi không có Docker → làm được trên máy công ty.
- **Chạy thử với LLM tắt và đọc như người dùng** tìm ra 5 lỗi mà test đơn vị không bắt (xem learning log Phase 2).
- **Commit nhỏ theo task**, mỗi task có DoD và ghi chú trạng thái thật trong checklist.

## Cắt / hoãn

- SSE tiến trình thật (đang giả lập theo thời gian, T9.01).
- Bản đồ (T9.03).
- Thí nghiệm topK/threshold (T1.28) cho tới khi có baseline thật.

## Đổi

- **Môi trường:** cần Docker + key để đóng các task còn "MỘT PHẦN"; trên máy công ty chỉ chạy được test offline.
- **Điểm ngân sách** làm các điểm "đinh" nhưng đắt (Bà Nà) bị điểm thấp; đang bù bằng quy tắc "mỗi sở thích có đại diện". Xem lại công thức khi có phản hồi người dùng.
- **Frontend chưa có test hành vi trong trình duyệt** (chỉ smoke test render + logic thuần). Cân nhắc Playwright cho kịch bản 1 ở Phase 9.

## Nhận xét người dùng thử (điền sau)

1. …
2. …
3. …
