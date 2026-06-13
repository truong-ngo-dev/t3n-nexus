# Bounded Contexts — t3n-nexus

Kết quả phân tích domain. Mỗi BC là một service độc lập.
Service mapping: `../architecture/service-mapping.md`. Architecture decisions: `../architecture/adr/`.

---

## Tổng quan

### Core Domain

| BC              | Service               | Trách nhiệm chính                                          |
|-----------------|-----------------------|------------------------------------------------------------|
| Catalog         | `catalog-service`     | Product, Category, SKU, Warranty attribute                 |
| Inventory       | `inventory-service`   | Stock level, Reservation, Limited offer (high-concurrency) |
| Warehouse       | `warehouse-service`   | **Phase 2** — platform-managed warehouse, Event Sourcing   |
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

### Generic Subdomain

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

### Infrastructure

| Service            | Trách nhiệm chính                                                           |
|--------------------|-----------------------------------------------------------------------------|
| `api-gateway`      | Spring Cloud Gateway — entry point, routing, rate limiting                  |
| `web-gateway`      | BFF — 1 shared cho 3 Angular app, httpOnly cookie                           |
| `oauth2-service`   | Authentication, authorization code flow, MFA, social login, session (reuse) |
| `identity-service` | User profile, credentials, Device, LoginActivity, ABAC (reuse)              |

---

## BC Relationships

```
[Upstream → Downstream]

catalog         → search          (Projection — ProductPublished/Updated/Unpublished)
inventory       → order           (Saga reply — InventoryReserved / ReservationFailed)
inventory       → search          (Projection — stock level cho filter)
inventory       → notification    (Notify — StockDepleted, StockReplenished)
order           → inventory       (Saga step — OrderCreated trigger reservation)
order           → payment         (Saga step — PaymentRequested)
order           → fulfillment     (Saga step — OrderConfirmed trigger shipment)
order           → notification    (Notify — OrderConfirmed, OrderCancelled, OrderCompleted)
order           → customer        (Projection — order history read model)
payment         → order           (Saga reply — PaymentSucceeded / PaymentFailed)
payment         → notification    (Notify — PaymentSucceeded, SellerPayoutCompleted)
fulfillment     → order           (Saga step — ShipmentPickedUp, ShipmentDelivered)
fulfillment     → payment         (COD trigger — ShipmentDelivered)
fulfillment     → notification    (Notify — ShipmentAssigned, InTransit, Delivered, Failed)
return          → fulfillment     (Trigger pickup — ReturnApproved)
return          → payment         (Trigger refund — ReturnCompleted)
return          → notification    (Notify — ReturnRequested, Approved, Rejected)
review          → search          (Projection — product rating aggregate)
review          → seller          (Projection — seller rating)
review          → shipper         (Projection — shipper rating)
seller          → search          (Projection — SellerRatingUpdated)
customer        → notification    (Notify — LoyaltyPointsEarned, Expired)
chat            → notification    (Notify — NewMessageReceived khi recipient offline)
```

---

## Detail theo BC

### Core Domain

#### Catalog BC

- Quản lý sản phẩm, danh mục, SKU
- Thông tin bảo hành là **attribute của sản phẩm**, không tách BC riêng
- Seller publish/unpublish sản phẩm
- Đẩy dữ liệu sang Search BC qua event (`ProductPublished`, `ProductUpdated`, `ProductUnpublished`, `CategoryUpdated`)

#### Inventory BC

- Theo dõi số lượng tồn kho theo SKU
- Reservation khi tạo đơn, release khi huỷ đơn
- Restock event trigger Notification BC
- **Limited offer config** — khi ACTIVE trên một SKU: request mua hàng đi vào Kafka queue, chỉ N slot được chấp nhận, phần còn lại reject. Race condition xử lý qua Redis atomic DECR + Lua script. Bloom Filter loại sớm sold-out check trước khi hit Redis.

#### Warehouse BC `[PLANNED — Phase 2]`

- Chỉ implement khi platform vận hành kho vật lý (platform-fulfilled — tương tự Amazon FBA)
- Hiện tại seller-fulfilled — Inventory BC đủ cho quantity tracking
- Khi implement: quản lý vị trí kho (bin, kệ), nhập hàng (seller gửi vào kho), xuất hàng (fulfillment lấy hàng)
- **Event Sourcing trên movement aggregate**: `GoodsReceived`, `MovedToBin`, `PickedForFulfillment`, `ShippedOut`
- Có thể thêm sau mà không đụng đến Inventory BC hay Fulfillment BC

#### Cart BC

- Guest cart: lưu server-side theo session token (Redis), tồn tại đến khi session hết hạn
- Persistent cart: customer đã đăng nhập
- **Merge khi login/register**: cùng SKU → qty = guest qty + user qty; item chỉ có ở một bên → giữ nguyên
- Tính giá realtime: gọi Pricing BC qua gRPC
- Validate voucher lúc checkout: gọi Promotion BC qua REST
- Tự động xoá giỏ hàng bỏ quên sau TTL (Scheduler trigger)

#### Order BC

- Vòng đời: `CREATED → CONFIRMED → FULFILLING → DELIVERED → COMPLETED | CANCELLED`
- **Saga sống ở đây** — `order-service` là state machine điều phối Payment, Inventory, Fulfillment
- Compensating transaction khi bất kỳ bước nào thất bại
- **Event Sourcing trên Order aggregate** — đầy đủ audit trail theo thời gian

#### Payment BC

- Tích hợp cổng thanh toán giả lập
- Hỗ trợ: prepaid (thẻ/ví điện tử giả lập), COD
- COD reconciliation sau khi giao hàng thành công (trigger từ `ShipmentDelivered`)
- Tính commission của platform: delegate sang Pricing BC
- Batch payout cho seller: Scheduler trigger hàng ngày

#### Fulfillment BC

- Kết nối đơn hàng đã confirm với shipper khả dụng
- Quy tắc phân công: theo zone, cân bằng tải — **Rule Engine (Drools)**
- Trạng thái: `ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED | FAILED`
- Xử lý giao thất bại: thử lại hoặc return-to-warehouse
- Proof of delivery: ảnh lưu trên MinIO
- **Auto-assignment pool nằm ở đây** — Shipper BC chỉ expose trạng thái và zone

#### Return & Refund BC

- Customer khởi tạo yêu cầu hoàn hàng
- **Temporal workflow**: customer request → seller review → admin phân xử (nếu tranh chấp) → shipper lấy hàng → warehouse nhận → trigger hoàn tiền
- Lý do hoàn hàng ảnh hưởng đến rating của seller
- Admin không có kênh chat — tranh chấp xử lý qua BC này

---

### Supporting Domain

#### Seller BC

- Onboarding: đăng ký → nộp thông tin → admin duyệt
- **Temporal workflow** cho approval flow — KYC ở mức tối giản (không xác minh giấy tờ, yêu cầu ID là được)
- Seller tier ảnh hưởng đến commission rate tại Pricing BC
- Chỉ số hiệu suất: tỉ lệ hoàn thành đơn, rating (feed từ Review BC)

#### Customer BC

- Profile, nhiều địa chỉ giao hàng
- Lịch sử đơn hàng: read model, lấy từ Order BC events
- **Loyalty Points Ledger pattern**:
  - Mọi thay đổi điểm là entry bất biến: `EARN` | `REDEEM` | `EXPIRE` | `REFUND` | `ADJUST`
  - Balance = SUM toàn bộ ledger
  - Mỗi EARN entry có `expireAt`
  - Scheduler BC chạy job hàng ngày tạo EXPIRE entry cho batch quá hạn
  - Khi redeem: ưu tiên trừ batch sắp hết hạn trước (FIFO by expiry)
- Không có tier — tránh thêm business rules mà không tăng độ sâu kỹ thuật

#### Shipper BC

- Đăng ký thủ công: `PENDING → ACTIVE | REJECTED`, admin duyệt và gán zone
- **Availability state machine qua MQTT**: shipper app publish lên EMQX `OFFLINE → ONLINE → BUSY → ONLINE`
- Location tracking: shipper publish GPS định kỳ lên EMQX, Fulfillment BC consume để hiển thị realtime
- Chỉ số hiệu suất: tỉ lệ đúng giờ, tỉ lệ giao thành công

#### Pricing BC

- **Rule Engine (Drools)** — computational service, không có side effect
- Tính phí vận chuyển: zone × trọng lượng × kích thước
- Commission của platform: theo seller tier và danh mục sản phẩm
- Expose **gRPC interface** — gọi từ Cart BC và Fulfillment BC

#### Promotion BC

- **Voucher**: mã giảm giá, loại (`PERCENTAGE | FIXED_AMOUNT`), giá trị đơn tối thiểu, số lượt dùng tối đa, thời hạn hiệu lực
- **Limited offer config**: config theo SKU (`maxQuantity`, `windowSeconds`) để trigger kịch bản test high-concurrency — không phải nghiệp vụ flash sale đầy đủ
- Race condition khi dùng voucher: Redis atomic INCR trước khi ghi DB
- Admin tạo/vô hiệu hoá voucher và limited offer config

#### Review & Rating BC

- Customer đánh giá sản phẩm sau khi đơn `COMPLETED`
- Customer đánh giá shipper sau khi nhận hàng
- Rating tổng hợp vào profile seller/shipper qua event
- Mỗi order item chỉ được đánh giá một lần

#### Search BC

- Elasticsearch là primary store — không có MySQL
- Consume event từ Catalog BC và Inventory BC để maintain index
- Hỗ trợ: full-text search, faceted filter (danh mục, khoảng giá, rating), filter theo tồn kho
- Pure read/query model — không có write path từ client

---

### Generic Subdomain

#### IAM BC (`oauth2-service` + `identity-service`)

**`oauth2-service`** — Authorization Server (Spring Authorization Server):
- `UserCredential`: email, hashedPassword, role, status (replica từ identity-service)
- `SocialIdentity`: provider, providerSub, userId — social login lookup
- `MfaConfig`: userId, enabled, method (EMAIL)
- `OAuthSession`: sid, userId, deviceId — wrap SAS OAuth2Authorization
- Authorization Code Flow + PKCE, opaque refresh token, JWT access token
- Registration entry point — tạo UserCredential, publish `UserRegistered` event
- Nhận event từ identity-service để sync UserCredential.status

**`identity-service`** — Identity & User Management:
- `UserAccount`: userId, email, fullName, phoneNumber, status — source of truth
- `EmailVerification`: token, userId, expiresAt — registration/verification flow
- `Device`: deviceId, userId, fingerprint, userAgent, ip — consume event từ oauth2-service
- `LoginActivity`: userId, deviceId, status, ip, timestamp — consume event từ oauth2-service
- ABAC/RBAC policy management — in-process enforcement, không có network call
- Account lock/unlock: source of truth, publish `UserLocked`/`UserUnlocked` → oauth2-service sync

**Roles**: `GUEST`, `CUSTOMER`, `SELLER`, `SHIPPER`, `ADMIN` — 1 user 1 role.

**Credential sync flow**:
```
identity-service publish → oauth2-service consume
  UserActivated  → UserCredential.status = ACTIVE
  UserLocked     → UserCredential.status = LOCKED
  UserUnlocked   → UserCredential.status = ACTIVE
```

**Device flow** (oauth2-service generate, identity-service own):
```
oauth2-service: hash(userAgent + ip + ...) → deviceId, lưu vào OAuthSession
                publish DeviceLoginRecorded { deviceId, userId, ... }
identity-service: consume → upsert Device entity, append LoginActivity
```

#### Notification BC

- Consume domain event từ tất cả BC
- Kênh: in-app (WebSocket Gateway), email, push notification
- Message theo template, mapping với từng loại event
- notification-service gọi trực tiếp SMTP/FCM — Kafka consumer retry cho failed delivery
- Persist notification vào PostgreSQL — client fetch lại qua `GET /notifications?since=` khi reconnect
- Publish tới Redis Pub/Sub `notif:user:{userId}` → WebSocket Gateway deliver

**WebSocket Gateway** (service riêng, không phải Notification BC):
- Quản lý toàn bộ WebSocket connection lifecycle của browser client
- Không chứa business logic — chỉ nhận từ Redis Pub/Sub và push tới đúng connection
- Scale horizontal: mọi instance subscribe Redis, instance nào giữ connection của userId thì push
- Auth tại handshake (validate JWT), không cần sticky session

```
Any BC → Kafka → Notification BC → Redis Pub/Sub → WebSocket Gateway → Browser
                                └→ SMTP / FCM   → Email / Push
```

#### Chat BC

- **MQTT (EMQX)** — không dùng STOMP over WebSocket
- **Customer ↔ Seller theo cặp**: 1 conversation per `(customerId, sellerId)`, không gắn với order cụ thể
- Dùng chung cho pre-purchase (hỏi sản phẩm) và post-purchase (hỏi về đơn hàng)
- `conversationId` = định danh duy nhất của cặp
- Topic pattern: `chat/conversation/{conversationId}`, `chat/user/{userId}/unread`
- Lưu message trên MongoDB
- Đếm tin nhắn chưa đọc, trigger Notification BC khi recipient offline
- EMQX xử lý connection management — không đi qua WebSocket Gateway

#### Workflow BC

- **Temporal** làm workflow engine
- Host các business process long-running:
  - Seller onboarding approval
  - Return & refund flow
  - Dispute resolution
- Đảm bảo ordering, retry với idempotency, compensating action

#### Scheduler BC

- Job định kỳ và delayed:
  - Tự động huỷ đơn chưa thanh toán sau N phút
  - Batch payout cho seller (hàng ngày)
  - Cảnh báo restock khi tồn kho dưới ngưỡng
  - Cleanup giỏ hàng bỏ quên
  - Tạo EXPIRE entry cho loyalty points quá hạn
  - Trigger tổng hợp báo cáo
- Spring Batch + Quartz hoặc Temporal scheduled workflows

#### Reporting BC

- ELT pipeline: Kafka → Flink/Spark → Data Warehouse
- Star schema: `fact_order`, `fact_payment`, `fact_delivery`, `fact_inventory_movement` × `dim_seller`, `dim_customer`, `dim_product`, `dim_shipper`, `dim_time`, `dim_zone`
- Elasticsearch cho operational reporting (query nhanh)

#### Simulator Service *(dev/staging only)*

Service giả lập actor và kịch bản khó test thủ công. REST API, trigger từ Admin Portal tab **Dev Tools**.

| Module                        | Chức năng                                                                                         |
|-------------------------------|---------------------------------------------------------------------------------------------------|
| Shipper Simulator             | Publish MQTT location theo pre-defined route, trigger state transitions                           |
| Concurrency Simulator         | N virtual users tấn công cùng resource — `LIMITED_OFFER`, `VOUCHER_RACE`, `INVENTORY_RESERVATION` |
| Saga Failure Injector         | Inject failure tại `PAYMENT` / `INVENTORY` / `FULFILLMENT`, verify compensating transaction       |
| Temporal Workflow Simulator   | Trigger workflows với outcome tuỳ chọn, advance time cho timeout test                             |
| Scheduler Trigger             | Gọi job ngay lập tức: `CART_CLEANUP`, `BATCH_PAYOUT`, `POINT_EXPIRY`, `AUTO_CANCEL_ORDER`         |
| Notification Stress Simulator | Gửi N notification tới 1 user trong thời gian ngắn, verify WebSocket Gateway + Redis Pub/Sub      |

---

## Quyết định quan trọng

- Mỗi đơn hàng thuộc đúng **1 seller** — không có mixed cart
- Bảo hành là attribute của **Catalog BC**, không tách riêng
- **Warehouse BC là Phase 2** — seller-fulfilled hiện tại, Inventory BC đủ dùng
- Account lock/unlock: gọi `identity-service` — domain BC không tự quản lý
- **Admin không có kênh chat** — disputes qua Return BC + Temporal
- **Promotion BC không có UI flash sale** — limited offer chỉ là config kỹ thuật để test high-concurrency
- **Loyalty point không có tier** — tránh thêm business rules mà không tăng độ sâu kỹ thuật
- **Shipper auto-assignment pool nằm ở Fulfillment BC** (Rule Engine) — Shipper BC chỉ expose trạng thái và zone
- **KYC seller ở mức tối giản** — không xác minh giấy tờ, yêu cầu ID là được
