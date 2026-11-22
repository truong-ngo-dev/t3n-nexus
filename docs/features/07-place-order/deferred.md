# Deferred — place-order

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác.

---

## 1. Notification cho Seller khi `OrderConfirmed`/`OrderCancelled` — chặn bởi `seller-service` chưa tồn tại

**Làm khi nào:** Sau khi `seller-service` được build với luồng đăng ký/duyệt seller thật (gắn `userId` cho mỗi `Seller`).

**Vấn đề:**
`notification-service` cần báo cho cả buyer lẫn seller khi đơn được confirm/cancel (`design.md` Happy Path). Buyer resolve được (customer có live session tại thời điểm `POST /orders` — xem mục 2 dưới). Seller thì không:

`event-catalog.md` đã document `seller-service` publish `SellerApproved`/`SellerRejected` với payload `{sellerId, userId}` — 2 field **tách riêng**, cho thấy `Seller` dự định là aggregate riêng (hồ sơ shop, quy trình duyệt) khác với `userId` (tài khoản đăng nhập). Nghĩa là resolve seller contact info là bài toán **2 hop**: `sellerId → userId (thuộc seller-service) → email (thuộc identity-service)`.

**Trạng thái hiện tại:**
`services/` chưa có thư mục `seller-service` — chưa build gì cả. `sellerId` xuất hiện ở `catalog-service.Product.sellerId`/`order-service.Order.sellerId` chỉ là `String` trần, không có gì backing. `catalog-service`'s `X-Seller-Id` header cũng là placeholder tạm cùng lý do (xem `catalogue_feature/deferred.md` #1 — cùng root cause: chưa có Seller auth/service thật).

**Cần làm khi `seller-service` có thật:**
- `seller-service` publish event mang `sellerId` + `userId` (đã có sẵn ý tưởng qua `SellerApproved`/`SellerRejected`)
- `notification-service` (hoặc bất kỳ nơi nào cần resolve contact info) build local read-model `userId → email` qua subscribe event từ `identity-service` — dùng chung cho cả customer lẫn seller một khi đã có `userId` (xem mục 2 để biết vì sao customer dùng thẳng `userId` không cần hop qua đâu cả)
- `order-service.Order.sellerId` cần xác nhận lại đang trỏ tới ID nào (`seller-service` aggregate ID hay `userId` trực tiếp) — hiện chưa rõ vì `seller-service` chưa tồn tại để đối chiếu

**Files liên quan:**
- `order-service`: `domain/order/OrderConfirmedEvent.java`, `OrderCancelledEvent.java` (đã có `sellerId`, đúng hướng, chỉ chưa resolve được contact info)
- `notification-service`: cần Kafka consumer mới cho `OrderConfirmed`/`OrderCancelled` (Phase 6, `implementation.md`) — phần seller trong handler này bị chặn bởi mục này

---

## 2. Email vào JWT claim — cân nhắc, chưa quyết

**Làm khi nào:** Trước khi build `notification-service` Phase 6 nếu chọn hướng này (ảnh hưởng cách `order-service` resolve email buyer).

**Vấn đề:**
`security-architecture.md` §Token Model hiện chỉ liệt kê JWT chứa `userId`, `role`, `deviceId`, `sessionId` — không có `email`. Buyer có live session tại thời điểm `POST /orders`, nhưng nếu JWT không mang email thì `order-service` vẫn không tự resolve được để snapshot lên `Order`/truyền qua `OrderConfirmedEvent`.

**Điểm khác biệt quan trọng so với lo ngại thông thường khi nhét PII vào JWT:** email trong hệ thống này **là username, bất biến** — loại bỏ rủi ro chính (claim cũ lệch dữ liệu gốc sau khi user đổi email). Token cũng không bao giờ ra browser (BFF pattern), giảm rủi ro lộ.

**Đề xuất:** thêm `email` vào JWT claim, cập nhật `security-architecture.md` §Token Model (living-reference, không cần ADR — không phải quyết định khó đảo ngược). Chưa làm — cần bạn chốt trước.

**Files liên quan:**
- `oauth2-service`: nơi JWT được issue (Spring Authorization Server config)
- `docs/global/2.architecture/7. security-architecture.md` §Token Model
