# Feature 10: Cổng Người bán và Vận hành (mỗi ứng dụng một BFF)

> **Trạng thái:** Đã làm `2026-11-06` · **Chặng:** C1a · **Loại:** Kỹ thuật
>
> **Nguồn:** Roadmap C1a (giao diện), `stage.md` mục 6, ADR-0006 (nguồn gốc theo `Host`, đặt tên BFF), ADR-0007 (workspace giao diện) · **Kiểm chứng bằng:** mọi kịch bản S1 đến S9 đi qua cổng (kiểm cuối ở feature 13)
>
> **Cùng thư mục:** `tech.md` (giải pháp, câu hỏi đã chốt), `plan.md`.

## 1. Mục tiêu và phạm vi

Người bán và nhân viên Vận hành đăng nhập, giữ phiên, gọi API của mình qua trình duyệt **mà không bao giờ cầm token**: mỗi ứng dụng có BFF riêng giữ token phía máy chủ, client OAuth2 riêng, CSRF, nguồn gốc riêng (ADR-0006).

Trong phạm vi:
- `seller-web-bff` (host `seller.localhost`, client `seller-web`) và `ops-web-bff` (host `ops.localhost`, client `ops-web`).
- Thư viện dùng chung `libs/web-bff-starter` chứa phần cơ chế mà `storefront-web-bff` đang giữ; `storefront-web-bff` chuyển sang dùng nó.
- `oauth2-service`: đăng ký client BFF theo cấu hình, báo thu hồi phiên cho đúng BFF của client.
- `api-gateway`: thêm host `seller`, tuyến tới BFF và máy chủ giao diện của từng host.

Ngoài phạm vi (feature 11, 12): dự án Angular `seller-web`, `ops-web` (kích hoạt ADR-0007). Chỉ kiểm bằng HTTP.

## 2. Đầu vào và đầu ra

| Ứng dụng | Host | BFF (cổng) | Client OAuth2 | Vai cần có | Tuyến tới |
|---|---|---|---|---|---|
| Cửa hàng | `shop.localhost` | `storefront-web-bff` (8090) | `storefront-web` | `USER` | giữ nguyên |
| Người bán | `seller.localhost` | `seller-web-bff` (8091) | `seller-web` | `USER` (có hồ sơ người bán) | `seller-service`, `catalog-service`, `oauth2-service` |
| Vận hành | `ops.localhost` | `ops-web-bff` (8092) | `ops-web` | `ADMIN` | `seller-service`, `catalog-service`, `audit-service`, `oauth2-service` |

Đường dẫn của BFF giữ một dạng: `/api/{dịch vụ}/**` viết lại thành `/api/**` (như `storefront-web-bff`), cộng `/bff/auth/**`, `/oauth2/authorization/**`, `/login/oauth2/code/**`. Phân quyền theo vai và quyền sở hữu vẫn do từng service quyết định; BFF **không** là chỗ chặn nghiệp vụ.

## 3. Luồng chính

### 3.1 Đăng nhập một ứng dụng
1. Trình duyệt vào `https://seller.localhost` (cổng). Chưa có phiên thì giao diện gọi `GET /bff/auth/login`; BFF chuyển tới `/oauth2/authorization/seller-web`.
2. Code flow có PKCE tới `auth.localhost`; `oauth2-service` cấp token cho client `seller-web`, tạo `sid` theo cặp (phiên người dùng, client).
3. BFF đổi code lấy token, giữ **phía máy chủ** (Redis), ghi ánh xạ `seller:sid:<sid>` ↔ phiên BFF, đặt cookie phiên và `XSRF-TOKEN` của chính host đó, chuyển về trang đích của ứng dụng.
4. Ba ứng dụng độc lập: đăng nhập ở Cửa hàng không cho phiên ở Người bán (cookie theo máy, ADR-0006).

### 3.2 Gọi API
Giao diện gọi `/api/catalog/seller/products` cùng nguồn gốc kèm tiêu đề CSRF → BFF kiểm CSRF và phiên, làm mới token nếu sắp hết hạn (gửi `sid`), gắn token, viết lại đường dẫn, chuyển tới `catalog-service`.

### 3.3 Đăng xuất và thu hồi
- Đăng xuất ở một ứng dụng là **đăng xuất một lần** (feature 03): kết thúc phiên người dùng của thiết bị và mọi phiên ứng dụng bên dưới (Cửa hàng, Người bán, Vận hành đã đăng nhập trên thiết bị đó). Thiết bị khác, người dùng khác không đổi.
- BFF nhận lệnh đăng xuất xóa ngay phiên và ánh xạ của mình, trả `202` kèm địa chỉ đăng xuất của Xác thực. Khi `oauth2-service` kết thúc các phiên ứng dụng, nó **gọi nội bộ từng BFF có phiên** (đúng BFF của client đó, token dịch vụ mang phạm vi của BFF đó) để xóa phiên BFF còn lại (lũy đẳng theo `sid`). Client không có BFF thì bỏ qua.

## 4. Đường không suôn sẻ và các điểm cần rà

| # | Tình huống | Hành vi cần có |
|---|---|---|
| E1 | Chưa đăng nhập gọi `/api/**` | 401 theo khung lỗi chung, không chuyển hướng |
| E2 | Thiếu hoặc sai CSRF ở phương thức ghi | 403, không chuyển tiếp |
| E3 | Người dùng `USER` đăng nhập ở Vận hành rồi gọi `/api/catalog/admin/**` | Service trả 403; BFF không biến thành 200 (BFF chỉ chuyển tiếp, phân quyền ở service) |
| E4 | Đăng xuất ở Người bán khi cùng thiết bị cũng đang đăng nhập Cửa hàng | Cả hai phiên kết thúc (đăng xuất một lần), mỗi BFF xóa khóa của mình; phiên của người dùng khác hoặc thiết bị khác **không** bị đụng |
| E5 | `oauth2-service` báo thu hồi một phiên ứng dụng | Chỉ BFF của client chứa phiên đó nhận lời gọi, kèm token phạm vi `<ứng dụng>.internal`; BFF không có phiên của người đó không nhận gì |
| E6 | Gọi `/bff/internal/**` không có token dịch vụ hoặc sai phạm vi (`storefront.internal` hay `ops.internal` gọi vào seller) | 401/403, không xóa gì |
| E7 | Host lạ | 404 ở cổng (đã có) |
| E8 | Ba BFF dùng chung Redis | Tiền tố khóa (`seller:`, `ops:`, `storefront:`) và tên cookie phiên (`SELLER_SESSION`, `OPS_SESSION`, `STOREFRONT_SESSION`) theo ứng dụng, không đè nhau |
| E11 | Tuyến của ứng dụng khác (vd `/api/customer/**` ở Người bán) | Không có tuyến: BFF chỉ chuyển tới các dịch vụ nó khai báo |
| E9 | Redirect URI và trang đích đăng xuất của client | Chỉ đúng host của ứng dụng; sai thì Xác thực từ chối |
| E10 | `storefront-web-bff` sau khi chuyển sang thư viện | Toàn bộ test hiện có vẫn xanh, hành vi không đổi |

## 5. Trạng thái sau khi xong

- Ba BFF cùng chạy từ một thư viện; thêm ứng dụng web mới là thêm một module mỏng và một khối cấu hình.
- `oauth2-service` có ba client BFF cấu hình bằng thuộc tính; không còn chỗ nào viết cứng `storefront-web` ngoài cấu hình mặc định.
- Qua cổng: đăng nhập, gọi API, đăng xuất một lần, thu hồi hoạt động riêng cho từng host.

## 6. Test cần có

- **Thư viện:** bộ test tích hợp của `storefront-web-bff` (53 ca) ở lại làm bộ kiểm cho thư viện; thêm bộ 6 ca cho mỗi BFF mới (E8, E6, E11, khóa, cookie, tuyến).
- **Mỗi BFF mới:** `ApplicationContext` khởi động; bảng tuyến đúng (đường dẫn viết lại tới dịch vụ giả); login chuyển đúng `registrationId`.
- **`oauth2-service`:** seeder đăng ký đủ client từ cấu hình (thiếu bí mật ngoài dev thì không khởi động); bộ báo thu hồi gọi đúng BFF theo client (E5), bỏ qua client không có BFF.
- **`api-gateway`:** host `seller` chuyển tới BFF và máy chủ giao diện của nó; luật tần suất nhận host `seller`.
- **Chạy thật (ngăn xếp `probe-`):** Postgres, Redis, `oauth2-service`, ba BFF, cổng, một dịch vụ nội bộ giả; kịch bản E1 đến E8 bằng HTTP.

## 7. Việc đã giao cho feature này

| Việc | Ghi chú |
|---|---|
| Hoãn: ẩn nút theo vai, bản đồ quyền cho giao diện | Hoãn tới khi có nhiều vai nội bộ (`00-features.md` dòng 12) |
| Hoãn: giới hạn đăng nhập theo vai ở BFF (`USER` vào Vận hành) | Hiện chỉ service từ chối (E3); xem `tech.md` Q5 |
