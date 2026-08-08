# ADR-010 — Order dùng CRUD, không Event Sourcing

**Status:** Accepted

## Context

`Order` aggregate được build ban đầu (session 2026-07-07/08, xem `docs/feature/place-order/implementation.md` mục "Lịch sử — Event Sourcing") bằng Event Sourcing — `event-sourcing-starter` lib, `event_store` table, `EventSourcedAggregateRoot`. Quyết định này được ghi rải rác ở nhiều doc global (`3. service-mapping.md`, `1. bounded-contexts.md`, `1.requirement/requirement.md`, `testing-plan.md`, `3.technical/tech-stack.md`) nhưng chưa từng có ADR riêng.

Khi implement `CREATED`-state timeout cho feature "đặt hàng" (session 2026-08-03, xem `docs/feature/place-order/implementation.md` Phase 5 — lúc đó feature này tách thành `payment-checkout`, đã gộp lại `place-order` 2026-08-08), phát hiện vấn đề thật: cần 1 scheduled job query "mọi order có `status='CREATED'` và quá hạn" — Event Sourcing thuần không hỗ trợ query theo field hiện tại kiểu này, chỉ query được theo `aggregate_id`. Giải quyết đòi hỏi bolt-on thêm 1 bảng projection riêng chỉ để phục vụ 1 nhu cầu vận hành tầm thường.

Việc này khiến phải đánh giá lại: `Order` có thật sự cần Event Sourcing không? Theo 4 tiêu chí đáng để trả chi phí ES:

1. **Temporal query** ("state tại thời điểm T quá khứ") — không có yêu cầu nghiệp vụ nào cho việc này.
2. **Invariant cần replay để tính đúng** — không: `canProcess()` (state machine guard) chỉ check `status` hiện tại, logic giống hệt dù load qua replay hay qua 1 `SELECT` CRUD.
3. **Optimistic concurrency** — ES cho qua `UNIQUE(aggregate_id, revision)`, nhưng `@Version` JPA field (đã dùng cho `Stock` bên inventory-service) cho đúng bảo vệ này, rẻ hơn nhiều.
4. **Audit/history** — phục vụ được bằng Kafka topic retention (`order.order.*`, qua Outbox đã có — ADR-005) hoặc 1 bảng `order_status_history` đơn giản nếu cần, không cần cả cơ chế ES.

Không tiêu chí nào đúng cho `Order`. Thêm 1 dữ kiện: `order-service` là **consumer duy nhất** của `event-sourcing-starter` trong toàn hệ thống — chi phí xây dựng/maintain lib không được amortize qua aggregate nào khác.

Đáng chú ý: `order-service/implementation.md` Phase 0 (dòng viết lúc quyết định ES ban đầu) đã tự ghi chú "candidate mạnh nhất nếu cần [Event Sourcing] sau: Loyalty Points Ledger bên customer-service" — ý tưởng này đã tồn tại từ đầu nhưng chưa hành động.

## Decision

`Order` chuyển sang **CRUD thường** — bảng `orders` (1 row/order, `status` column, `@Version` cho optimistic concurrency), không dùng `event-sourcing-starter` nữa.

`event-sourcing-starter` **không bị xoá** — giữ lại, dự định dùng cho **`Loyalty Points Ledger`** (customer-service, chưa build) khi tới lượt: bản chất ledger (EARN/REDEEM/EXPIRE/ADJUST, đã thiết kế insert-only trong `customer-service/data.md`), phân tán ghi theo `customerId` (không dồn vào 1 hot aggregate như `Stock` lúc flash sale — khác biệt quan trọng so với `Order`, nơi throughput toàn hệ thống đi qua cùng 1 loại aggregate), có giá trị audit thật (dispute "sao tôi có từng này điểm") — đúng use case kinh điển của Event Sourcing/ledger pattern.

`Saga choreography` (ADR-004) và `Transactional Outbox` (ADR-005) cho `Order` **không đổi** — `Order` vẫn là Saga coordinator, vẫn publish event qua Outbox y hệt trước; quyết định này chỉ đổi cách *persist state nội bộ* của aggregate, không đổi cách nó giao tiếp với service khác.

## Consequences

**+** `orders` table tự query được theo field hiện tại (`status`, deadline...) — không cần bảng projection phụ cho `CREATED`-timeout hay bất kỳ nhu cầu vận hành tương tự nào sau này  
**+** Đơn giản hơn: bỏ `event_store`, bỏ dual-constructor (business + reconstitution) trên mỗi event, bỏ `rehydrate()`/`loadFromHistory()`  
**+** `event-sourcing-starter` không lãng phí — dùng lại nguyên vẹn cho Loyalty Points Ledger  
**−** Đã tốn 1 session build Phase 0 (ES infra) + phần đầu Phase 1 (Order event-sourced) cho `order-service` trước khi phát hiện vấn đề — chi phí chìm, không thu hồi được, nhưng hạ tầng (`event-sourcing-starter` lib) không mất, chuyển mục đích sử dụng  
**−** Mất audit trail per-event chi tiết của riêng `Order` (trước đây replay được toàn bộ lịch sử biến đổi qua `event_store`) — audit giờ dựa vào Kafka topic retention (`order.order.*`) qua Outbox, đủ cho nhu cầu hiện tại nhưng không có replay-to-any-point-in-time

## Cập nhật doc liên quan

Các doc sau đã ghi "Order — Event Sourcing" trước ADR này, cần đọc kèm ADR này để hiểu đã đổi: `2.architecture/3. service-mapping.md`, `2.architecture/1. bounded-contexts.md`, `1.requirement/requirement.md`, `testing-plan.md`, `service/order-service/implementation.md` (Phase 0/1 — giữ nguyên làm lịch sử, đánh dấu reverted, không xoá).
