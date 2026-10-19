# Cổng ngoài cùng: service (`api-gateway`)

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-10-18` · **Thư mục mã:** `services/api-gateway`
>
> Tầng kỹ thuật của điểm vào duy nhất từ ngoài. **Không có `analysis.md`** (không có nghiệp vụ) và **không có `api.yaml`, `data.md`**: cổng chỉ định tuyến và bảo vệ, bảng tuyến ở mục 7.3, khóa Redis ở mục 12.1. File này là nguồn thiết kế duy nhất; nhiều mục khuôn mẫu ghi "N/A".
>
> Mô tả thiết kế **đích** sau feature 06 ([`stage/c0-foundation/06-access-gateway/`](../../stage/c0-foundation/06-access-gateway/flow.md)), gồm định tuyến theo `Host` (ADR-0006). Phần chưa thuộc phạm vi 06 (tuyến di động, tuyến sẽ sửa khi làm feature liên quan) ghi rõ ở mục 15 và 18.
>
> BFF đứng sau: [`storefront-web-bff`](../storefront-web-bff/service.md).

## 1. Tổng quan kỹ thuật

Điểm vào duy nhất từ ngoài (một cổng, 8000). **Định tuyến theo `Host`**, bảo vệ thô ở biên (host lạ, giới hạn tần suất theo địa chỉ mạng), đặt địa chỉ client tin cậy và ghi log yêu cầu; không giữ phiên, không xác thực, không biết nghiệp vụ.

- **Loại service:** gateway định tuyến thuần (không có domain).
- **Stack:** Java 21, Spring Boot 4, Spring Cloud Gateway Server WebFlux, Redis phản ứng (chỉ cho giới hạn tần suất), thư viện `reactive-rate-limiter-starter`.
- **Kiến trúc trong service:** cấu hình tuyến và bộ lọc toàn cục. **Module:** `infrastructure/configuration` (tuyến, CORS), `infrastructure/ratelimit`, các bộ lọc host, địa chỉ client, log.

---

## 2. Ánh xạ domain → code

N/A: không có aggregate, thuật ngữ nghiệp vụ hay trạng thái. Khái niệm kỹ thuật:

| Khái niệm | Nghĩa | Biểu diễn |
|---|---|---|
| Host cho phép | Tên máy mà cổng nhận (`auth`, `shop`, `ops` + miền gốc) | Thuộc tính `app.gateway.hosts.*`, kiểm bởi bộ lọc host |
| Luật theo đường dẫn | Một luật giới hạn tần suất theo (host, phương thức, đường dẫn) | Đối tượng bất biến nạp từ `app.ratelimit.rules[i].*` (T1) |
| Mức chung | Giới hạn mặc định theo địa chỉ mạng | `app.ratelimit.default.*` (300 mỗi 60 giây) |
| Địa chỉ client tin cậy | Địa chỉ của kết nối TCP tới cổng | Tiêu đề `X-Client-Ip` do cổng đặt (T7) |

---

## 3. Đảm bảo quy tắc

Quy tắc **kỹ thuật và bảo mật** (không phải bất biến nghiệp vụ). Cột Test trỏ về kịch bản của feature 06 (`flow.md` mục 6); `—` nghĩa là chưa có test.

| ID | Quy tắc | Loại | Cơ chế | Test |
|---|---|---|---|---|
| `Q-01` | `Host` ngoài danh sách cho phép trả 404 trước mọi bộ lọc khác, không đếm gì | Liên tục | Tuyến chọn theo tên máy bằng bộ so khớp riêng (`HostMatcher`: không phân biệt chữ hoa, bỏ cổng); tên máy lạ không khớp tuyến nào nên Gateway trả 404 trước mọi bộ lọc, không cần bộ lọc host riêng (T9) | KT-26 (`HostRoutingTest`) |
| `Q-02` | Địa chỉ client là địa chỉ kết nối; mọi tiêu đề `X-Forwarded-*`, `Forwarded`, `X-Client-Ip` do client gửi bị bỏ; `X-Forwarded-Host`, `-Proto`, `-Port` đặt từ yêu cầu thật (Gateway **không tự đặt** các tiêu đề này, đã kiểm) | Liên tục | `ForwardedHeadersSanitizer` (T9); `X-Client-Ip` đặt lại từ địa chỉ kết nối (T7) | KT-27, 24 (`ForwardedHeadersTest`); KT-04 (`ClientAddressTest`) |
| `Q-03` | Luật theo đường dẫn chạy trước mức chung; mỗi tầng chỉ đếm yêu cầu nó cho qua, yêu cầu bị chặn ở một tầng không làm tăng bộ đếm tầng sau; **tuyến tới máy chủ giao diện không bị đếm ở mức chung** (tài nguyên tĩnh, máy chủ phát triển phục vụ hàng trăm tệp mỗi lần mở trang; `FrontendRateLimitExemptionTest`) | Liên tục | Bộ lọc giới hạn hai tầng (T1) | KT-01, 02, 04, 07, 33 (`ratelimit/`) |
| `Q-04` | 429 có thân khung lỗi chung (`TOO_MANY_REQUESTS`) và `Retry-After` là thời gian còn lại đến khi giải phóng một lượt | Liên tục | Bộ lọc giới hạn, thư viện trả thời gian chờ (T2) | KT-01, 03, 06 (`ratelimit/`, `GatewayFrameworkTest`) |
| `Q-05` | Redis lỗi hoặc chậm thì mở cửa kèm cảnh báo; lệnh đếm có thời gian chờ ngắn | Liên tục | Thư viện giới hạn (T3) | KT-05 (`RedisOutageTest`, `ReactiveRedisRateLimiterTest`) |
| `Q-06` | Cấu hình luật sai thì không khởi động | Liên tục | Kiểm hợp lệ lúc nạp (T1) | KT-06 (`RateLimitPolicyTest`) |
| `Q-07` | CORS đọc từ cấu hình, dùng mẫu nguồn gốc, không dùng `*` kèm cookie | Liên tục | `CorsWebFilter` với `allowedOriginPatterns` (T9) | KT-35 (`GatewayCorsTest`) |
| `Q-08` | Cổng không xác thực, không giữ phiên, không đọc nội dung nghiệp vụ | Liên tục | Chỉ định tuyến và bộ lọc theo địa chỉ, host, đường dẫn | N/A |

---

## 4. Commands

N/A: không có lệnh nghiệp vụ.

---

## 5. Queries

N/A: không có truy vấn.

---

## 6. Use cases: tham gia

| Feature | Vai trò | Bước của service này | Bước bù trừ | Publishes |
|---|---|---|---|---|
| [01 Đăng ký](../../stage/c0-foundation/01-register/flow.md) | Biên bảo vệ | Giới hạn `POST /register` ở host `auth` (10 mỗi giờ mỗi địa chỉ) | Không có | Không |
| [06 Cổng truy cập](../../stage/c0-foundation/06-access-gateway/flow.md) | Chủ của feature | Định tuyến theo `Host`, giới hạn tần suất, địa chỉ client, log | Không có | Không |

---

## 7. Integration contract

### 7.1 Publishes, 7.2 Consumes

N/A: không phát hay nhận sự kiện.

### 7.3 Sync calls

**Bảng tuyến (đích, theo `Host`):**

| Host (dev) | Đường dẫn | Đích | Ghi chú |
|---|---|---|---|
| `auth.localhost` | mọi đường dẫn | `oauth2-service` (8004) | Không tiền tố, không cắt đường dẫn |
| `shop.localhost` | `/api/**`, `/oauth2/authorization/**`, `/login/oauth2/code/**`, `/bff/**` | `storefront-web-bff` (8090) | Đường dẫn của BFF (tên đã chốt ở bước 4b của feature 06) |
| `shop.localhost` | còn lại | Máy chủ giao diện Cửa hàng (`app.gateway.frontends.shop`, feature 05) | Giao diện không dùng các tiền tố của BFF |
| `ops.localhost` | — | Chưa có (giữ chỗ cho `ops-web-bff`) | Làm khi có ứng dụng Vận hành |
| Di động | — | Chưa chốt (`TQ-02`) | ADR-0006: BFF di động riêng, cách định tuyến chưa quyết |

Mọi tuyến chuyển tiếp: giữ `Host` của trình duyệt, đặt `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Forwarded-Port` từ yêu cầu thật (`Q-02`); `X-Client-Ip` ở bước log của feature 06.

| Chiều | Đối tác | Giao thức | Endpoint | Timeout | Retry | Khi đối tác lỗi |
|---|---|---|---|---|---|---|
| Outbound | `oauth2-service`, `storefront-web-bff`, máy chủ giao diện | HTTP | Theo bảng tuyến | Mặc định của Gateway | Không | Trả lỗi của Gateway (502, 504), không thử lại |
| Outbound | Redis | Redis phản ứng | Đếm giới hạn tần suất | Ngắn (T3) | Không | Mở cửa kèm cảnh báo (`Q-05`) |

---

## 8. Projection, 9. Concurrency, 11. Job định kỳ

N/A. Đếm giới hạn là một đoạn Lua nguyên tử trên Redis; hạn khóa bằng TTL, không có job.

---

## 10. Độ tin cậy

| Hạng mục | Thiết kế | Phục vụ |
|---|---|---|
| Redis chết | Mở cửa kèm cảnh báo, lệnh đếm có thời gian chờ ngắn để yêu cầu không treo | `Q-05` |
| Dịch vụ sau chết | 502 hoặc 504 của Gateway; không thử lại | Mục 7.3 |

---

## 12. NFR → giải pháp

Chưa có phân tích nghiệp vụ nên chưa có kỳ vọng từ analysis. NFR của chặng đo ở feature 08 (điểm cuối rỗng qua cổng là một phép đo trần).

### 12.1 Giới hạn tần suất (tóm tắt)

| Tầng | Khóa Redis | Mức mặc định | Khi vượt |
|---|---|---|---|
| Luật theo (host, phương thức, đường dẫn) | `ratelimit:rule:<tên luật>:<địa chỉ>` | Theo từng luật; luật đầu `POST /register` ở `auth`: 10 mỗi 3600 giây | 429 + `Retry-After` |
| Mức chung theo địa chỉ | `ratelimit:ip:<địa chỉ>` | 300 mỗi 60 giây | 429 + `Retry-After` |

Giới hạn **theo người dùng** nằm ở BFF (khóa `ratelimit:user:`), không ở cổng (`rate-limiting-layers.md` mục 2.1).

### 12.2 Observability

Một dòng log hoàn tất mỗi yêu cầu (cùng tên trường với feature 07, WebFlux), `traceId` truyền xuống bằng `traceparent`, `client.ip` đúng. Chỉ số cần theo dõi: số 429 theo luật, số lần Redis lỗi, số 404 do host lạ, độ trễ qua cổng.

---

## 13. Quyền & danh tính

Cổng không xác thực và không biết danh tính; xác thực nằm ở BFF và Xác thực, quyền ở dịch vụ sau.

---

## 14. Quyết định kỹ thuật

Quyết định riêng của feature 06 nằm ở [`tech.md`](../../stage/c0-foundation/06-access-gateway/tech.md); file này chỉ trỏ.

| ID | Câu hỏi | Chọn | Phục vụ |
|---|---|---|---|
| `T-01` | Luật theo đường dẫn, đếm hai tầng | → `tech.md` T1 | `Q-03`, `Q-06` |
| `T-02` | Thân 429, `Retry-After` | → `tech.md` T2 | `Q-04` |
| `T-03` | Thời gian chờ của lệnh đếm | → `tech.md` T3 | `Q-05` |
| `T-04` | Địa chỉ client tin cậy, log | → `tech.md` T7 | `Q-02` |
| `T-05` | Nguồn gốc theo subdomain, tiêu đề chuyển tiếp, CORS | → `tech.md` T9, [`ADR-0006`](../../global/2.architecture/adr/0006-application-origins-and-host-routing.md) | `Q-01`, `Q-02`, `Q-07` |

---

## 15. Chưa chốt (kỹ thuật)

| ID | Câu hỏi | Ảnh hưởng | Phụ thuộc |
|---|---|---|---|
| `TQ-02` | Cách định tuyến cho nhiều BFF di động (một host kèm đường dẫn theo ứng dụng, hay mỗi ứng dụng một host) | Mục 7.3 | Khi làm BFF di động đầu tiên (ADR-0006) |
| `TQ-03` | Địa chỉ máy chủ giao diện của từng ứng dụng web | Mục 7.3 | Feature 05 |

---

## 16. Lệch giữa thiết kế và code

Chưa rà code.

---

## 17. Dependencies

- **Services:** `oauth2-service`, `storefront-web-bff`, máy chủ giao diện (feature 05).
- **Infrastructure:** Redis (chỉ cho giới hạn tần suất).

---

## 18. Nợ kỹ thuật

| ID | Hạng mục | Hiện trạng | Hệ quả nếu chưa trả | Vì sao hoãn | Trả khi nào | Liên quan |
|---|---|---|---|---|---|---|
| `TD-01` | Ngắt mạch cho lệnh đếm Redis | Mỗi yêu cầu chờ hết thời gian chờ ngắn khi Redis chết | Cảnh báo và chờ mỗi yêu cầu | Đủ cho C0 | Khi Redis chết lâu hoặc có tải thật | `Q-05`, T3 |
| `TD-02` | Địa chỉ kết nối của cổng có thể là địa chỉ cầu của Docker khi triển khai bằng Docker Compose | Chạy ngoài Docker ở dev nên chưa gặp | Giới hạn và log theo địa chỉ sai | Chưa triển khai | Trước demo | `Q-02`, T7 |
| `TD-03` | Tuyến di động (`/mobile/**` trỏ `mobile-gateway`, chưa có service) | Giữ chỗ, không còn trong thiết kế mới | Không | Chưa có ứng dụng di động | Khi làm BFF di động đầu tiên | `TQ-02` |
| `TD-04` | Tuyến và danh sách công khai của các dịch vụ nghiệp vụ cũ đi qua BFF, theo thiết kế cũ | Chưa thiết kế lại | Có thể không khớp mô hình danh tính mới | Chưa có feature của các BC đó | **Sửa khi làm feature của BC tương ứng** (xem `storefront-web-bff` `TD-05`) | Mục 7.3 |
| `TD-05` | Giới hạn nhiều thể hiện: khóa đếm chung Redis đủ, nhưng địa chỉ client sau bộ cân bằng tải cần đổi cách lấy | Một thể hiện ở C0 | Địa chỉ client sai khi có bộ cân bằng tải | Chưa có bộ cân bằng tải | Khi có bộ cân bằng tải trước cổng | `Q-02`, R2 |
| `TD-06` | WebSocket tự tải lại của `ng serve` (Vite) hỏng khi đi qua cổng: bắt tay Gateway tới Vite nhận 400 và đóng ngay, Vite client tải lại trang liên tục | Cách tránh: chạy `ng serve` với `--live-reload=false --hmr=false` khi mở qua cổng; chưa tìm nguyên nhân gốc (nghi `Origin`, `Host` của đích hoặc mã thông báo WebSocket của Vite 6.4) | Giao diện không tự tải lại khi sửa mã; nếu quên tắt thì vòng lặp tải lại | Chỉ ảnh hưởng môi trường phát triển giao diện | Khi làm feature 05 (cấu hình `ng serve` hoặc proxy), hoặc khi điều tra WebSocket qua cổng | `TQ-03`, `tech.md` (mục chạy giao diện thật) |
| `TD-07` | Khi máy chủ giao diện chết, cổng trả 500 kèm dòng ERROR "Connection refused" thay vì 502 hoặc 503 có khung lỗi | Gặp khi chạy thật (giao diện không chạy, mở `/customer`) | Người dùng thấy lỗi chung, log có ERROR | Chỉ xảy ra khi giao diện không chạy | Khi làm feature 05 hoặc xử lý lỗi tuyến của cổng | `TQ-03` |

## Tài liệu liên quan

- [`../storefront-web-bff/service.md`](../storefront-web-bff/service.md): BFF đứng sau cổng.
- [`stage/c0-foundation/06-access-gateway/`](../../stage/c0-foundation/06-access-gateway/flow.md): feature đang làm cho cổng.
- ADR-0006 ở `global/2.architecture/adr/`; `global/3.technical/rate-limiting-layers.md`.
