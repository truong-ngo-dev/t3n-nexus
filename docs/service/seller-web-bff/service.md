# Cổng ứng dụng Người bán, kênh web: service (`seller-web-bff`)

> **Trạng thái:** Đã làm (feature 10) · **Cập nhật:** `2026-11-06` · **Thư mục mã:** `services/seller-web-bff`

## 1. Tổng quan kỹ thuật

BFF kênh web của ứng dụng Người bán (host `seller`, cổng 8091, client `seller-web`, ADR-0006). Vai cần có: Người bán `USER`. BFF chỉ chuyển tiếp API, phân quyền theo vai và quyền sở hữu do từng service sau quyết định.

- **Loại service:** gateway kiểu BFF (không có domain).
- **Stack:** Java 21, Spring Boot 4, thư viện `web-bff-starter` (Spring Cloud Gateway Server WebFlux, Spring Security OAuth2 Client/Resource Server, Spring Session Redis, Redis phản ứng).
- **Cấu hình chính:** lớp khởi động mang chú thích `@EnableWebBff`, `bff.app-name=seller`, cookie phiên `SELLER_SESSION`, tiền tố khóa Redis `seller:`, token dịch vụ nội bộ phạm vi `seller.internal`.

---

## 7.3 Sync calls

| Chiều | Đối tác | Giao thức | Endpoint | Ghi chú |
|---|---|---|---|---|
| Outbound | Xác thực (`auth` host) | Chuyển hướng | `GET /oauth2/authorize` | Trình duyệt điều hướng đăng nhập |
| Outbound | Xác thực | REST | `POST /oauth2/token` | Đổi mã; làm mới kèm `sid` |
| Outbound | Xác thực | REST | `GET /oauth2/jwks` | Xác thực token dịch vụ nội bộ |
| Outbound | `oauth2-service` | REST kèm token | `/api/oauth2/**` → `/api/**` | Tuyến dịch vụ xác thực |
| Outbound | `seller-service` | REST kèm token | `/api/seller/**` → `/api/**` | Tuyến dịch vụ người bán (ví dụ `/api/seller/seller/profile` → `/api/seller/profile`) |
| Outbound | `catalog-service` | REST kèm token | `/api/catalog/**` → `/api/**` | Tuyến quản lý hàng hóa (ví dụ `/api/catalog/seller/products` → `/api/seller/products`) |
| Inbound | Xác thực | REST, token dịch vụ | `POST /bff/internal/sessions/revoke` | Phạm vi `seller.internal` |
| Inbound | `websocket-gateway` | Redis | `wsticket:<ticket>` | Bắt tay WebSocket |

---

## 13. Quyền & danh tính

- **Danh tính:** Phiên BFF (cookie `SELLER_SESSION`) → token truy cập; claim `sub`, `role`, `sid`.
- **Vai cần có:** Người bán mang vai `USER`.
- **Phân quyền:** BFF chỉ chuyển tiếp kèm token (`TokenRelay`), không phân quyền nghiệp vụ; kiểm tra vai và quyền sở hữu do từng dịch vụ sau tự quyết định.
- **Nội bộ:** Token dịch vụ phạm vi `seller.internal` cho điểm cuối `POST /bff/internal/sessions/revoke`.

---

## 14. Quyết định kỹ thuật

- Mỗi cổng/ứng dụng một BFF riêng theo ADR-0006 (`seller-web-bff` phục vụ riêng ứng dụng Người bán trên host `seller`).
- Toàn bộ mã và cơ chế hoạt động dùng chung nằm ở thư viện `services/libs/web-bff-starter`. Module này chỉ chứa lớp khởi động `@EnableWebBff` và cấu hình khai báo.

---

## 17. Dependencies

- **Services:** Xác thực (`oauth2-service`), `api-gateway` đứng trước, các dịch vụ sau: `seller-service`, `catalog-service`.
- **Thư viện:** `libs/web-bff-starter`.
- **Infrastructure:** Redis (phiên, ánh xạ `sid`, ticket WebSocket, giới hạn tần suất).

---

Phần còn lại giống `storefront-web-bff`: xem `../storefront-web-bff/service.md` và thư viện `web-bff-starter`.
