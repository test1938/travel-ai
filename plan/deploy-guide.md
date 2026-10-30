# Hướng dẫn deploy miễn phí (T9.06)

Cách khuyên dùng: **một máy ảo AWS EC2 chạy `compose.prod.yml`**. Cả app chạy trên một máy: Postgres + pgvector,
backend và Caddy (HTTPS tự động). Không cần dịch vụ quản lý nào khác.

> **Điều kiện miễn phí (kiểm tra ngày 2026-10-30, có thể đổi):**
> - Tài khoản AWS tạo sau 15/07/2025 nhận 100 USD credit, cộng thêm tối đa 100 USD khi làm một số việc gợi ý trong
>   console.
> - Free plan kéo dài 6 tháng, hoặc hết khi dùng hết credit.
> - Máy được dùng trong free plan gồm t3.micro, t3.small, t4g.micro, t4g.small… Dùng **t3.small (2 vCPU, 2 GB)**,
>   khoảng 15 USD/tháng, trừ vào credit.
> - IPv4 công khai cũng tính phí (khoảng 3,6 USD/tháng) và cũng trừ vào credit.
>
> Free plan không tự chuyển sang trả phí. Muốn chạy tiếp sau 6 tháng thì phải nâng cấp tài khoản, hoặc chuyển
> sang Oracle Cloud Always Free (cuối trang).

## 0. Chuẩn bị

- Repo đã push lên GitHub (public, hoặc private kèm deploy key).
- Một key AI, chọn **một** trong hai (đổi qua lại sau bằng `AI_PROVIDER`, xem ADR-011):
  - **Gemini** ở [Google AI Studio](https://aistudio.google.com/apikey).
    - Dùng free tier (không gắn thẻ) thì không bao giờ bị tính tiền, chỉ bị giới hạn số request.
    - Nếu bật billing, hãy đặt budget alert trong Google Cloud Billing.
  - **OpenAI** ở [platform.openai.com](https://platform.openai.com/api-keys).
    - Phải nạp trước tối thiểu 5 USD.
    - Mặc định dùng `gpt-4.1-mini`, khoảng 0,004 USD/lịch. Muốn rẻ nhất thì đặt `OPENAI_CHAT_MODEL=gpt-4.1-nano`,
      khoảng 0,001 USD/lịch.
    - Đặt *usage limit* trong Settings → Limits.
- Một tên miền miễn phí:
  - **DuckDNS**: vào [duckdns.org](https://www.duckdns.org), đăng nhập, tạo `tenban.duckdns.org`.
  - **sslip.io**: không cần đăng ký. Tên miền có dạng `3-112-45-67.sslip.io`, tức IP của máy với dấu chấm đổi
    thành gạch ngang.

## 1. Tạo máy ảo EC2

1. Đăng ký AWS → chọn **Free plan**. Chọn region gần Việt Nam, ví dụ `ap-southeast-1` (Singapore).
2. Vào **Billing → Budgets → Create budget → Zero spend budget** để có email cảnh báo nếu phát sinh tiền ngoài
   credit.
3. Vào **EC2 → Launch instance**:
   - Name: `travelpilot`.
   - AMI: **Ubuntu Server 24.04 LTS (x86_64)**.
   - Instance type: **t3.small**. t3.micro chỉ 1 GB RAM, quá ít để build image.
   - Key pair: tạo mới, tải file `.pem` về.
   - Network: Allow SSH from **My IP**, Allow HTTP, Allow HTTPS.
   - Storage: 20 GB gp3. Free plan có 30 GB EBS.
4. Vào **Elastic IPs → Allocate → Associate** với máy vừa tạo, để IP không đổi khi khởi động lại.
5. DuckDNS: điền IP này vào ô `current ip` của tên miền.

## 2. Cài Docker trên máy

```bash
ssh -i travelpilot.pem ubuntu@<ELASTIC_IP>

curl -fsSL https://get.docker.com | sudo sh           # Docker Engine + compose plugin
sudo usermod -aG docker ubuntu && newgrp docker

# 2 GB swap: build Maven + JVM + Postgres trên 2 GB RAM cần thêm chỗ
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 3. Lấy code và cấu hình

```bash
git clone https://github.com/<owner>/travelpilot-app.git
cd travelpilot-app
cp .env.example .env
openssl rand -hex 32        # copy làm GUEST_TOKEN_SECRET
openssl rand -hex 16        # copy làm DB_PASSWORD
nano .env
```

Các dòng cần sửa trong `.env`:

```
AI_PROVIDER=google-genai             # hoặc openai
GEMINI_API_KEY=<key Gemini>          # nếu AI_PROVIDER=google-genai
OPENAI_API_KEY=<key OpenAI>          # nếu AI_PROVIDER=openai
GUEST_TOKEN_SECRET=<64 ký tự từ openssl>
DB_PASSWORD=<32 ký tự từ openssl>
SITE_ADDRESS=tenban.duckdns.org      # hoặc 3-112-45-67.sslip.io; ":80" nếu chỉ thử HTTP
```

## 4. Chạy

```bash
docker compose -f compose.prod.yml up -d --build      # lần đầu build 5-10 phút
docker compose -f compose.prod.yml logs -f be         # đợi "Seeded 101 catalog places" và "Ingested 401 knowledge chunks"
```

Mở `https://tenban.duckdns.org`. Caddy tự xin chứng chỉ Let's Encrypt trong lần truy cập đầu.

## 5. Kiểm tra sau deploy

- Chạy kịch bản 1 (gia đình, Đà Nẵng): các bước tiến trình sáng dần, có lịch, có bản đồ, chat được.
- `https://<domain>/internal/search?q=x&dest=DANANG` phải trả 404, vì `/internal` không mở ra ngoài.
- `https://<domain>/actuator/health` phải trả 404, vì Caddy chỉ proxy `/api`.
- `docker compose -f compose.prod.yml logs be | head`: log là JSON và có `requestId`.

## 6. Vận hành

| Việc | Lệnh |
|---|---|
| Đổi Gemini ↔ OpenAI | sửa `AI_PROVIDER` (và key) trong `.env`, rồi `docker compose -f compose.prod.yml up -d`. Lần khởi động đầu sau khi đổi sẽ tự embed lại (~1 phút) |
| Cập nhật code | `git pull && docker compose -f compose.prod.yml up -d --build` |
| Xem log | `docker compose -f compose.prod.yml logs -f be` |
| Sao lưu DB | `docker compose -f compose.prod.yml exec db pg_dump -U tp travelpilot > backup.sql` |
| Dừng (giữ dữ liệu) | `docker compose -f compose.prod.yml down` (đừng thêm `-v`, nó xóa volume) |
| Tiết kiệm credit | Stop instance trên console khi không demo. Elastic IP vẫn tính phí nhỏ khi máy tắt. |

Giới hạn chi phí đã có sẵn trong app:
- Mỗi guest: 10 lịch/giờ, 60 tin nhắn/giờ.
- Mỗi IP: 20 guest mới/giờ (IP thật được đọc qua `X-Forwarded-For` từ Caddy).
- Toàn hệ thống: 2.000 request gọi model/ngày.

## Phương án dự phòng: Oracle Cloud Always Free (miễn phí lâu dài)

- Gói Always Free vẫn có máy ARM **Ampere A1**.
  - Theo các nguồn thứ cấp năm 2026, gói này đã giảm còn tổng 2 OCPU / 12 GB RAM. Hãy kiểm tra trang Always Free
    của Oracle trước khi tạo.
  - Cấu hình này dư sức chạy app.
- Cách làm:
  1. Tạo VM.Standard.A1.Flex (2 OCPU, 12 GB), image Ubuntu 24.04.
  2. Mở port 80/443 trong Security List.
  3. Chạy thêm `sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT` và tương tự cho 443, vì image Ubuntu của
     Oracle chặn sẵn.
  4. Làm tiếp từ bước 2 ở trên.
- Image dùng trong compose (temurin, pgvector, caddy, node) đều có bản ARM64.
- Lưu ý:
  - Đăng ký cần thẻ.
  - Region phổ biến hay báo "Out of host capacity", khi đó thử availability domain khác.

## Vì sao không dùng PaaS free (Render, Koyeb, Neon…)?

- Gói free thường chỉ có 512 MB RAM. JVM + Spring AI rất sát giới hạn đó.
- Service ngủ sau vài phút không dùng, nên lần mở đầu chờ 30-60 giây.
- Postgres free của Render hết hạn sau 30 ngày.
- Vẫn làm được (Neon free có pgvector, backend chạy image `be/Dockerfile`), nhưng phải tách FE ra static hosting
  và cấu hình CORS/proxy. Phức tạp hơn một máy ảo chạy compose.
