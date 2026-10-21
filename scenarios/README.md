# scenarios: kiểm đầu-cuối cả chặng C0 (feature 09)

Dựng chuỗi dịch vụ thật (Xác thực 8004, BFF 8090, cổng 8000, giao diện 4201) và chạy S1 đến S5 cùng các kiểm chéo qua cổng. Giải thích, kết quả và
phá thử: `docs/stage/c0-foundation/09-e2e-check/tech.md`. Kịch bản là bộ Playwright thật ở `frontends/e2e-real/`.

| Lệnh | Việc |
|---|---|
| `scenarios/run.sh [--no-build]` | Cả chuỗi từ trạng thái sạch: số đầu, dựng, pha A (cổng luật đăng ký nâng), pha B (cổng luật mặc định, 429 của đăng ký), dừng, đối chiếu số dòng. Thoát khác 0 nếu có gì sai |
| `scenarios/up.sh [--rules relaxed\|default] [--only all\|gateway] [--no-build]` | Chỉ dựng chuỗi (hoặc chỉ khởi động lại cổng) |
| `scenarios/down.sh [--only gateway]` | Dừng đúng tiến trình do `up.sh` dựng (mã ở `.run/*.pid`) |

Yêu cầu: Git Bash, Docker với `infra-postgres-oauth2-1`, `infra-redis-1`, `infra-kafka-1` đang chạy, JDK 21 tại `C:\Program Files\Java\jdk-21`, Node; các cổng 8000, 8004, 8090, 4201, 8002 trống.

**Ghi vào hạ tầng dev** (Postgres `oauth2_db`, Redis, Kafka): dữ liệu thử có tiền tố `e2e-` được dọn và kiểm số dòng; tài khoản `ducthinh@dev.aplus.vn` giữ nguyên.
Nhật ký và số đầu/cuối ở `scenarios/.run/` (không vào git).
