# Cổng ứng dụng Vận hành, kênh web: service (`ops-web-bff`)

> **Trạng thái:** Đã làm (feature 10) · **Cập nhật:** `2026-11-06` · **Thư mục mã:** `services/ops-web-bff`

## 1. Tổng quan kỹ thuật

BFF kênh web của ứng dụng Vận hành (host `ops`, cổng 8092, client `ops-web`, ADR-0006). Vai cần có: Vận hành `ADMIN`. BFF chỉ chuyển tiếp API, phân quyền theo vai và quyền sở hữu do từng service sau quyết định.

- **Loại service:** gateway kiểu BFF (không có domain).
- **Stack:** Java 21, Spring Boot 4, thư viện `web-bff-starter` (Spring Cloud Gateway Server WebFlux, Spring Security OAuth2 Client/Resource Server, Spring Session Redis, Redis phản ứng).
- **Cấu hình chính:** lớp khởi động mang chú thích `@EnableWebBff`, `bff.app-name=ops`, cookie phiên `OPS_SESSION`, tiền tố khóa Redis `ops:`, token dịch vụ nội bộ phạm vi `ops.internal`.

---

## 7.3 Sync calls

| Chiều | Đối tác | Giao thức | Endpoint | Ghi chú |
|---|---|---|---|---|
| Outbound | Xác thực (`auth` host) | Chuyển hướng | `GET /oauth2/authorize` | Trình duyệt điều hướng đăng nhập |
| Outbound | Xác thực | REST | `POST /oauth2/token` | Đổi mã; làm mới kèm `sid` |
| Outbound | Xác thực | REST | `GET /oauth2/jwks` | Xác thực token dịch vụ nội bộ |
| Outbound | `oauth2-service` | REST kèm token | `/api/oauth2/**` → `/api/**` | Tuyến dịch vụ xác thực |
| Outbound | `seller-service` | REST kèm token | `/api/seller/**` → `/api/**` | Tuyến quản lý người bán |
| Outbound | `catalog-service` | REST kèm token | `/api/catalog/**` → `/api/**` | Tuyến quản lý danh mục / sản phẩm |
| Outbound | `audit-service` | REST kèm token | `/api/audit/**` → `/api/**` | Tuyến nhật ký kiểm toán |
| Inbound | Xác thực | REST, token dịch vụ | `POST /bff/internal/sessions/revoke` | Phạm vi `ops.internal` |
| Inbound | `websocket-gateway` | Redis | `wsticket:<ticket>` | Bắt tay WebSocket |

---

## 13. Quyền & danh tính

- **Danh tính:** Phiên BFF (cookie `OPS_SESSION`) → token truy cập; claim `sub`, `role`, `sid`.
- **Vai cần có:** Vận hành mang vai `ADMIN`.
- **Phân quyền:** BFF chỉ chuyển tiếp kèm token (`TokenRelay`), không phân quyền nghiệp vụ; kiểm tra vai do từng dịch vụ sau tự quyết định.
- **Nội bộ:** Token dịch vụ phạm vi `ops.internal` cho điểm cuối `POST /bff/internal/sessions/revoke`.

---

## 14. Quyết định kỹ thuật

- Mỗi cổng/ứng dụng một BFF riêng theo ADR-0006 (`ops-web-bff` phục vụ riêng ứng dụng Vận hành trên host `ops`).
- Toàn bộ mã và cơ chế hoạt động dùng chung nằm ở thư viện `services/libs/web-bff-starter`. Module này chỉ chứa lớp khởi động `@EnableWebBff` và cấu hình khai báo.

---

## 17. Dependencies

- **Services:** Xác thực (`oauth2-service`), `api-gateway` đứng trước, các dịch vụ sau: `seller-service`, `catalog-service`, `audit-service`.
- **Thư viện:** `libs/web-bff-starter`.
- **Infrastructure:** Redis (phiên, ánh xạ `sid`, ticket WebSocket, giới hạn tần suất).

---

Phần còn lại giống `storefront-web-bff`: xem `../storefront-web-bff/service.md` và thư viện `web-bff-starter`.
