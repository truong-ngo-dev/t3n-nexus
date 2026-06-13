# Phân Tích Domain — Nền Tảng Ecommerce

> Tài liệu Problem Space: NFR, Actors, Bounded Contexts, Technical Scenarios.
> Architecture decisions → `architecture/`. Service design → `design/`.

---

## 1. Tổng Quan Dự Án

Dự án cá nhân nhằm tổng hợp và thực hành toàn bộ kiến thức kỹ thuật đang có và muốn học.
Mô hình kinh doanh: **Multi-vendor marketplace** (mỗi đơn hàng thuộc về đúng một seller).
Nghiệp vụ ở mức tối giản — độ phức tạp tập trung ở tầng kỹ thuật.

**Frontend:** NX monorepo — 3 app (storefront, seller-portal, admin-portal)
**Backend:** Java 21 + Spring Boot, kiến trúc microservices

---

## 2. Yêu Cầu Phi Chức Năng (NFR)

### 2.1 Baseline — Normal Traffic

| Metric                      | Giá trị       | Ghi chú                             |
|-----------------------------|---------------|-------------------------------------|
| Concurrent users            | 20,000        | Tương đương Tiki VN mid-tier        |
| External RPS (read)         | ~8,000 req/s  | ~1 req / 2.5s per user              |
| External RPS (write)        | ~800 req/s    | Checkout, order create, cart update |
| Internal RPS (service mesh) | ~40,000 req/s | Fan-out ~5× per external request    |
| DAU                         | 200,000       | ~10% online đồng thời ở peak        |
| Orders / ngày               | ~50,000       |                                     |

### 2.2 Flash Sale — Spike Traffic

| Metric              | Giá trị       | Ghi chú                                   |
|---------------------|---------------|-------------------------------------------|
| Concurrent users    | 200,000       | 10× normal — Shopee VN flash sale thực tế |
| Peak RPS (read)     | ~80,000 req/s | Product page + countdown timer            |
| Peak RPS (checkout) | ~8,000 req/s  | Sau rate limiting tại api-gateway         |
| Duration            | 15–30 phút    | Đủ để trigger Saga timeout, auto-cancel   |

### 2.3 Latency SLAs

| Operation                           | P99 target         | Justify                              |
|-------------------------------------|--------------------|--------------------------------------|
| Search (full-text + filter)         | < 200ms            | Elasticsearch + Redis cache          |
| Product detail page                 | < 100ms            | Redis cache layer                    |
| Add to cart                         | < 200ms            | Redis primary store                  |
| Checkout (synchronous response)     | < 2s               | Saga async sau khi reserve inventory |
| Real-time location update (shipper) | < 500ms end-to-end | MQTT QoS 1 qua EMQX                  |
| In-app notification                 | < 1s end-to-end    | Redis Pub/Sub → WebSocket Gateway    |


### 2.5 Data Volume

| Entity                        | Volume          | Justify                                            |
|-------------------------------|-----------------|----------------------------------------------------|
| SKUs trong catalog            | 5,000,000       | Elasticsearch (SQL LIKE không scale ở mức này)     |
| Chat messages / ngày          | ~500,000        | MongoDB (document model phù hợp hơn relational)    |
| Notification events / ngày    | ~1,000,000      | Redis Pub/Sub + WebSocket Gateway horizontal scale |
| Order events (Event Sourcing) | ~250,000 / ngày | Kafka retention, audit trail                       |

---

## 3. Actors

| Actor     | Loại     | Mô tả                                            |
|-----------|----------|--------------------------------------------------|
| Guest     | External | Duyệt catalog, xem sản phẩm, không cần đăng nhập |
| Customer  | External | Mua hàng, theo dõi đơn, chat, nhận thông báo     |
| Seller    | External | Quản lý shop/sản phẩm, xử lý đơn hàng, chat      |
| Shipper   | External | Nhận và thực hiện giao vận                       |
| Admin     | External | Quản trị platform, duyệt seller, xử lý khiếu nại |
| Scheduler | Internal | Trigger các job định kỳ                          |
| Event Bus | Internal | Kafka                                             |

> Chi tiết tại [usecase-diagram][]

---

## 4. Bounded Contexts

> Chi tiết từng BC (business rules, technical patterns, edge cases): `domain/bounded-contexts.md`

### Core Domain

| BC              | Service               | Trách nhiệm chính                                          |
|-----------------|-----------------------|------------------------------------------------------------|
| Catalog         | `catalog-service`     | Product, Category, SKU, Warranty attribute                 |
| Inventory       | `inventory-service`   | Stock level, Reservation, Limited offer (high-concurrency) |
| Warehouse       | `warehouse-service`   | platform-managed warehouse, Event Sourcing                 |
| Cart            | `cart-service`        | Guest cart (Redis), persistent cart, merge khi login       |
| Order           | `order-service`       | Order lifecycle, Saga choreography, Event Sourcing         |
| Payment         | `payment-service`     | Payment processing, COD reconciliation, batch payout       |
| Fulfillment     | `fulfillment-service` | Shipper assignment (Rule Engine), shipment tracking        |
| Return & Refund | `return-service`      | Return flow, dispute resolution qua Temporal               |

### Supporting Domain

| BC              | Service             | Trách nhiệm chính                                        |
|-----------------|---------------------|----------------------------------------------------------|
| Seller          | `seller-service`    | Onboarding (Temporal), approval, rating                  |
| Customer        | `customer-service`  | Profile, Loyalty Points Ledger (expiration, FIFO redeem) |
| Shipper         | `shipper-service`   | Registration, availability state machine qua MQTT        |
| Pricing         | `pricing-service`   | Shipping fee + commission (Drools), gRPC interface       |
| Promotion       | `promotion-service` | Voucher, limited offer config, race condition via Redis  |
| Review & Rating | `review-service`    | Product/Seller/Shipper rating, unlock sau OrderCompleted |
| Search          | `search-service`    | Elasticsearch projections, full-text + faceted search    |

### Generic Domain

| BC           | Service                | Trách nhiệm chính                                                   |
|--------------|------------------------|---------------------------------------------------------------------|
| IAM          | `oauth2-service`       | Authentication, authorization code flow, MFA, social login, session |
| IAM          | `identity-service`     | User profile, credentials, Device, LoginActivity, ABAC              |
| Notification | `notification-service` | Email, push, in-app routing qua Redis Pub/Sub                       |
| WebSocket GW | `websocket-gateway`    | In-app real-time delivery tới browser                               |
| Chat         | `chat-service`         | MQTT (EMQX), Customer ↔ Seller, persist MongoDB                     |
| Workflow     | `workflow-service`     | Temporal — dispute, seller onboarding, scheduled jobs               |
| Scheduler    | `scheduler-service`    | Cron triggers — auto-cancel, loyalty expiry, payout                 |
| Reporting    | `reporting-service`    | Analytics pipeline, star schema DWH                                 |
| Simulator    | `simulator-service`    | Dev/staging only — concurrency, saga, shipper sim                   |

---

## 5. Kịch Bản Kỹ Thuật

| Kịch bản                                       | BC liên quan                            | Pattern                                            |
|------------------------------------------------|-----------------------------------------|----------------------------------------------------|
| Tạo đơn hàng kèm reservation tồn kho           | Order + Inventory                       | Saga (choreography)                                |
| Thanh toán thất bại → rollback đơn hàng        | Order + Payment + Inventory             | Saga (compensating transaction)                    |
| Batch payout cho seller                        | Payment + Scheduler                     | Outbox + batch job                                 |
| Mua hàng limited offer dưới high concurrency   | Inventory + Promotion                   | Redis atomic + Kafka queue                         |
| Race condition dùng voucher                    | Promotion                               | Redis atomic INCR                                  |
| Lịch sử trạng thái đơn hàng                    | Order                                   | Event Sourcing                                     |
| Lịch sử di chuyển hàng hoá                     | Warehouse                               | Event Sourcing                                     |
| Seller onboarding                              | Seller + Workflow                       | Temporal workflow                                  |
| Hoàn hàng multi-step                           | Return & Refund + Workflow              | Temporal workflow                                  |
| Tính phí vận chuyển                            | Pricing                                 | Rule Engine (Drools) + gRPC                        |
| Tự động phân công shipper                      | Fulfillment + Pricing                   | Rule Engine                                        |
| Tìm kiếm sản phẩm có filter                    | Search                                  | Elasticsearch                                      |
| Theo dõi giao hàng realtime (customer)         | Fulfillment                             | WebSocket Gateway (server push)                    |
| Shipper gửi location update                    | Fulfillment + Shipper                   | MQTT (EMQX) — pub/sub                              |
| Pipeline phân tích doanh thu                   | Reporting                               | Flink/Spark → DWH                                  |
| Giả lập N user mua SKU limited offer đồng thời | Simulator + Inventory + Promotion       | Concurrency Simulator → Redis atomic + Kafka queue |
| Giả lập payment failure → verify rollback      | Simulator + Order + Payment + Inventory | Saga Failure Injector → compensating transaction   |
| Giả lập shipper di chuyển realtime             | Simulator + Fulfillment + Shipper       | Shipper Simulator → MQTT → WebSocket → browser     |
| Trigger scheduler job ngay lập tức             | Simulator + Scheduler                   | Scheduler Trigger (bypass cron)                    |

---

