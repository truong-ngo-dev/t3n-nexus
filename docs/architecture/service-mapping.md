# Service Mapping — t3n-nexus

Tổng cộng **23 services**. Mỗi BC thành một service riêng.
Chi tiết BC: `../domain/bounded-contexts.md`

## Infrastructure

| Service            | Nguồn                                       | Vai trò                                                 |
|--------------------|---------------------------------------------|---------------------------------------------------------|
| `api-gateway`      | Mới                                         | Spring Cloud Gateway — entry point, routing, rate limit |
| `web-gateway`      | Mới                                         | BFF duy nhất cho 3 Angular app, OAuth2 Client, proxy    |
| `oauth2-service`   | Mới                                         | Authentication, session, device, activity, social login |
| `identity-service` | Mới                                         | User profile, roles, ABAC, account lock/unlock          |

## Core Domain

| Service               | BC                                 |
|-----------------------|------------------------------------|
| `catalog-service`     | Catalog BC                         |
| `inventory-service`   | Inventory BC                       |
| `warehouse-service`   | Warehouse BC `[PLANNED — Phase 2]` |
| `cart-service`        | Cart BC                            |
| `order-service`       | Order BC — Saga sống ở đây         |
| `payment-service`     | Payment BC                         |
| `fulfillment-service` | Fulfillment BC                     |
| `return-service`      | Return & Refund BC                 |

## Supporting Domain

| Service             | BC                         |
|---------------------|----------------------------|
| `seller-service`    | Seller BC                  |
| `customer-service`  | Customer BC                |
| `shipper-service`   | Shipper BC                 |
| `pricing-service`   | Pricing BC — Drools + gRPC |
| `promotion-service` | Promotion BC               |
| `review-service`    | Review & Rating BC         |
| `search-service`    | Search BC — Elasticsearch  |

## Generic

| Service                | BC                             |
|------------------------|--------------------------------|
| `notification-service` | Notification BC                |
| `websocket-gateway`    | WebSocket Gateway              |
| `chat-service`         | Chat BC — MongoDB + EMQX       |
| `workflow-service`     | Workflow BC — Temporal host    |
| `scheduler-service`    | Scheduler BC                   |
| `reporting-service`    | Reporting BC                   |
| `simulator-service`    | Simulator *(dev/staging only)* |
