# Rate Limiting — 3 Lớp và Khi Nào Cần Thêm Lớp Thứ 3

> Cookbook quyết định: 1 endpoint mới có cần rate-limit riêng hay 2 lớp gateway có sẵn đã đủ.

---

## 2 lớp đã có sẵn — mọi request đều đi qua

| Lớp                   | Vị trí        | Key                                      | Mặc định      | Áp dụng cho                                     | Không áp dụng cho                                                                                                                               |
|-----------------------|---------------|------------------------------------------|---------------|-------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `IpRateLimitFilter`   | `api-gateway` | per-IP                                   | 300 req / 60s | **Mọi** path, kể cả `/auth/**` — hop ngoài cùng | —                                                                                                                                               |
| `UserRateLimitFilter` | `web-gateway` | per-user (từ `Authentication.getName()`) | 300 req / 60s | `/api/**` **đã authenticated**                  | Request chưa đăng nhập; `/auth/**` (route thẳng `api-gateway` → `oauth2-service`, bypass web-gateway hoàn toàn — xem `RouteConfiguration.java`) |

Cả 2 là **coarse edge safety net kỹ thuật** — chặn client hỏng/runaway hammer bất kỳ endpoint nào, không phải rule nghiệp vụ. Comment gốc trong `UserRateLimitFilter` đã tự nói rõ: *"Endpoint-specific business throttles (OTP, login) stay explicit in each service's application layer, independent of this filter."*

---

## Vì sao 2 lớp trên không đủ cho hành động nhạy cảm (register, resend OTP...)

1. **Ngưỡng quá lỏng cho mục đích chống lạm dụng.** 300 req/60s = 5 req/s per IP là để chặn DoS hạ tầng, không phải để chặn "1 IP tạo 300 tài khoản/phút" hay "spam trigger email xác minh". 2 mục tiêu khác hẳn nhau về độ chặt cần thiết.
2. **`/auth/**` không đi qua `UserRateLimitFilter`.** Vì route thẳng từ `api-gateway`, và vì action này luôn *chưa* authenticated — request đăng ký/login không thể có `Authentication` để filter đó tính key. Với nhóm endpoint này, lớp `api-gateway` (per-IP, ngưỡng lỏng) là **lớp gateway duy nhất** thực sự chạm tới.
3. **Per-IP không phân biệt được hành động.** Ngưỡng đủ chặt để chống spam-register sẽ chặn nhầm cả các request bình thường khác cùng IP (NAT, văn phòng, mobile carrier CGNAT) nếu áp chung 1 ngưỡng toàn cục.

## Quy tắc quyết định

Endpoint cần thêm rate-limit riêng ở tầng service (application layer, cùng transaction với business logic) khi **cả 3 đúng**:

1. Endpoint **thay đổi state** hoặc **trigger side-effect tốn tài nguyên bên thứ 3** (tạo account, gửi email/SMS, OTP) — không phải read thuần.
2. Endpoint **không yêu cầu authentication trước** (nên không được `UserRateLimitFilter` bảo vệ), hoặc dù có auth thì ngưỡng chung 300/60s vẫn quá lỏng so với chi phí thật của 1 lần gọi.
3. Ngưỡng nghiệp vụ hợp lý **thấp hơn nhiều bậc** so với 300/60s — tính theo key hẹp hơn IP (per-email, per-userId) khi có thể, vì IP có thể dùng chung (NAT).

## Áp dụng thực tế trong hệ thống

| Endpoint                                       | Key                                   | Limit   | Lớp gateway có chạm tới không                         |
|------------------------------------------------|---------------------------------------|---------|-------------------------------------------------------|
| `POST /auth/register`                          | per-IP (`clientIp`, chưa có identity) | 5 / giờ | Chỉ `api-gateway` (300/60s) — không đủ chặt, cần thêm |
| `POST /api/identity/users/resend-verification` | per-email                             | 3 / giờ | Chỉ `api-gateway` — cùng lý do                        |

## Cơ chế implement — `@RateLimit` (annotation + AOP), không phải Filter

Đặt trong `rate-limiter-starter` (`vn.t3nexus.lib.ratelimiter`), gồm:

- **`@RateLimit(key, limit, windowSeconds, message)`** — đặt trên method `CommandHandler.handle()`, `key` là SpEL expression evaluate trên tham số method (VD `"'register:' + #command.clientIp()"`).
- **`RateLimitAspect`** — `@Around` advice, evaluate key, gọi `RateLimiter.tryAcquire`, ném `RateLimitExceededException` nếu vượt. Order = `HIGHEST_PRECEDENCE` — chạy **trước** advisor của `@Transactional`, request vượt limit bị chặn trước khi mở transaction/tốn connection.
- **`RateLimitExceededException`** — đứng độc lập, **không** extend `DomainException`: rate-limit không phải business invariant của domain model (đúng command gửi lại sau khi hết window sẽ pass, domain state không đổi), mà là cross-cutting infra concern — extend `DomainException` sẽ làm mờ ranh giới "vi phạm business rule" vs "vi phạm throttle kỹ thuật". Luôn map 429 qua `RateLimitExceptionHandler` (`@RestControllerAdvice` tự chứa trong lib) — không cần mỗi service tự định nghĩa ErrorCode/Exception riêng cho rate-limit như trước.

**Vì sao annotation, không phải Filter:** Filter/`HandlerInterceptor` chạy trước khi Spring bind `@RequestBody` — muốn key theo field trong body (VD `email`) thì Filter phải tự parse JSON, trùng việc `@Valid`/`@RequestBody` sắp làm, và làm infra layer phải biết business schema. AOP trên `handle()` thì `Command` đã parse xong, key theo field nào cũng lấy được qua SpEL.

**Vì sao không cần Filter riêng để bắt request fail `@Valid`:** request vẫn đã chạm server (qua TCP/Tomcat) trước khi bất kỳ code nào — Filter hay AOP — chạy, nên "chặn sớm hơn" ở đây chỉ tiết kiệm được 1 lượt JSON parse + Bean Validation (micro giây), không đáng kể so với DB round-trip đã được chặn đúng chỗ. Bảo vệ volumetric DDoS thật là việc của `IpRateLimitFilter` ở api-gateway (đứng trước khi request chạm service), không phải lựa chọn cơ chế bên trong oauth2-service.

**Khi thêm 1 endpoint side-effect mới:** áp quy tắc 3 điều kiện ở trên. Nếu cả 3 đúng → thêm `@RateLimit` lên method `handle()`, key theo field hẹp nhất có sẵn trong Command tại thời điểm gọi (email/userId nếu có, IP nếu chưa có identity nào khác).
