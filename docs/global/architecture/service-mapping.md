# Service Mapping — t3n-nexus

23 services, mỗi BC thành một service riêng.
Chi tiết BC và business rules: `../../domain/bounded-contexts.md`

---

## 1. Service Inventory

### Infrastructure

| Service            | Trách nhiệm                                                     | Runtime    |
|--------------------|-----------------------------------------------------------------|------------|
| `api-gateway`      | Entry point, routing, rate limiting, auth filter                | Always-on  |
| `web-gateway`      | BFF cho 3 Angular app, session cookie, token exchange           | Always-on  |
| `oauth2-service`   | Authentication, OAuth2 flows, MFA, social login, session        | Always-on  |
| `identity-service` | User profile, roles, ABAC policies, account lock                | Always-on  |

### Core Domain

| Service               | BC              | Trách nhiệm                                                   | Runtime    |
|-----------------------|-----------------|---------------------------------------------------------------|------------|
| `catalog-service`     | Catalog         | Product, Category, SKU, Warranty attribute                    | Always-on  |
| `inventory-service`   | Inventory       | Stock level, Reservation, Limited offer (high-concurrency)    | Always-on  |
| `warehouse-service`   | Warehouse       | Platform-managed warehouse, Event Sourcing trên movement      | Phase 2    |
| `cart-service`        | Cart            | Guest cart (Redis), persistent cart, merge khi login          | Always-on  |
| `order-service`       | Order           | Order lifecycle, Saga choreography, Event Sourcing            | Always-on  |
| `payment-service`     | Payment         | Payment processing, COD reconciliation, batch payout          | Always-on  |
| `fulfillment-service` | Fulfillment     | Shipper assignment (Rule Engine), shipment tracking           | Always-on  |
| `return-service`      | Return & Refund | Return flow, dispute resolution qua Temporal                  | Always-on  |

### Supporting Domain

| Service             | BC              | Trách nhiệm                                                    | Runtime   |
|---------------------|-----------------|----------------------------------------------------------------|-----------|
| `seller-service`    | Seller          | Onboarding (Temporal), approval, rating                        | Always-on |
| `customer-service`  | Customer        | Profile, Loyalty Points Ledger (expiration, FIFO redeem)       | Always-on |
| `shipper-service`   | Shipper         | Registration, availability state machine, location via MQTT    | Always-on |
| `pricing-service`   | Pricing         | Shipping fee + commission (Drools), gRPC interface             | Always-on |
| `promotion-service` | Promotion       | Voucher, limited offer config, race condition via Redis        | Always-on |
| `review-service`    | Review & Rating | Product/Seller/Shipper rating, unlock sau OrderCompleted       | Always-on |
| `search-service`    | Search          | Elasticsearch projections, full-text + faceted search          | Always-on |

### Generic

| Service                | Trách nhiệm                                         | Runtime          |
|------------------------|-----------------------------------------------------|------------------|
| `notification-service` | Email, push, in-app routing qua Redis Pub/Sub       | Always-on        |
| `websocket-gateway`    | In-app real-time delivery tới browser               | Always-on        |
| `chat-service`         | Customer ↔ Seller messaging, persist MongoDB        | Always-on        |
| `workflow-service`     | Temporal host — dispute, onboarding, scheduled jobs | Always-on        |
| `scheduler-service`    | Cron triggers — auto-cancel, loyalty expiry, payout | Batch            |
| `reporting-service`    | Analytics pipeline, star schema DWH                 | Batch            |
| `simulator-service`    | Concurrency, saga, shipper simulation               | Dev/staging only |

---

## 2. Inter-service Dependencies

### Synchronous (REST / gRPC)

| Caller                | Callee            | Protocol | Kịch bản                               |
|-----------------------|-------------------|----------|----------------------------------------|
| `web-gateway`         | `oauth2-service`  | REST     | Token exchange, session validation     |
| `fulfillment-service` | `pricing-service` | gRPC     | Tính phí vận chuyển, phân công shipper |

### Asynchronous (Kafka)

| Publisher              | Consumer(s)                          | Kịch bản                                          |
|------------------------|--------------------------------------|---------------------------------------------------|
| `order-service`        | `inventory-service`                  | Saga — reserve stock khi OrderCreated             |
| `inventory-service`    | `order-service`                      | Saga — xác nhận / từ chối reservation             |
| `order-service`        | `payment-service`                    | Saga — trigger payment sau reservation thành công |
| `payment-service`      | `order-service`, `inventory-service` | Saga — compensate khi payment fail                |
| `order-service`        | `review-service`                     | Unlock review sau OrderCompleted                  |
| `scheduler-service`    | `order-service`                      | Auto-cancel đơn prepaid timeout                   |
| `scheduler-service`    | `payment-service`                    | Trigger batch payout cho seller                   |
| `scheduler-service`    | `customer-service`                   | Expire loyalty points                             |
| `scheduler-service`    | `cart-service`                       | Cleanup giỏ hàng bỏ quên                          |
| Domain events (many)   | `search-service`                     | Cập nhật Elasticsearch projection                 |
| Domain events (many)   | `notification-service`               | Routing và gửi thông báo                          |
| `notification-service` | `websocket-gateway`                  | Deliver in-app notification qua Redis Pub/Sub     |

### Temporal (Workflow)

| Initiator        | Workflow Host      | Kịch bản                       |
|------------------|--------------------|--------------------------------|
| `seller-service` | `workflow-service` | Seller onboarding multi-step   |
| `return-service` | `workflow-service` | Hoàn hàng + dispute resolution |

### MQTT (EMQX)

| Publisher      | Consumer                                  | Kịch bản                    |
|----------------|-------------------------------------------|-----------------------------|
| `simulator-service` *(Shipper chưa có mobile app)* | `shipper-service` → `fulfillment-service` | Location update realtime    |
| `chat-service`                                     | Customer / Seller app                     | Customer ↔ Seller messaging |

---

## 3. Data Ownership

| Store         | Owned by                                                                                                                                    |
|---------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| PostgreSQL    | catalog, inventory, order, payment, fulfillment, return, seller, customer, shipper, pricing, promotion, review, oauth2, identity, scheduler |
| MongoDB       | chat-service                                                                                                                                |
| Elasticsearch | search-service *(read model — nhận projection từ Kafka, không phải source of truth)*                                                        |
| Redis         | cart (guest session), notification pub/sub, idempotency keys, ABAC policy cache, inventory reservation atomic                               |
