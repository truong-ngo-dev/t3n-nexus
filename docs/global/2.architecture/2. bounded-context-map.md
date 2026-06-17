# Bounded Context Map — t3n-nexus

> Mô tả quan hệ upstream/downstream và integration pattern giữa các BC.
> Chi tiết service và data ownership: `service-mapping.md`

---

## Sơ đồ — Luồng nghiệp vụ chính

```
                        ┌─────────────────────────────────────────────────────┐
                        │                   IAM (Generic)                     │
                        │         oauth2-service + identity-service           │
                        │            Upstream / Conformist cho tất cả         │
                        └─────────────────────┬───────────────────────────────┘
                                              │ (tất cả services conform)
   ┌──────────┐    ┌──────────┐    ┌──────────▼───────────────────────────────┐
   │  Catalog │───▶│Inventory │    │             Purchase Flow                │
   │  (Core)  │    │  (Core)  │    │                                          │
   └──────────┘    └────┬─────┘    │  [Cart] ──▶ [Order] ◀──▶ [Inventory]     │
        │               │          │                │    (Saga Partnership)   │
        │ OHS/PL        │ U→D      │                │                         │
        ▼               ▼          │                ├──▶ [Payment]            │
   ┌──────────┐  ┌─────────────┐   │                │    (Saga U→D)           │
   │  Search  │  │  (SKU ref)  │   │                │                         │
   │(ACL+proj)│  └─────────────┘   │                └──▶ [Fulfillment]        │
   └──────────┘                    │                      │   (U→D)           │
                                   │                      ├──▶ [Pricing] gRPC │
                                   │                      └──▶ [Shipper]      │
                                   └─────────────────────────────────────────-┘
                                              │
              ┌───────────────────────────────┼───────────────────────────────┐
              │                               │                               │
              ▼                               ▼                               ▼
   ┌─────────────────┐              ┌───────────────────┐            ┌─────────────────┐
   │   Notification  │              │     Workflow      │            │    Scheduler    │
   │   (Generic)     │              │    (Generic)      │            │   (Generic)     │
   │  ← domain events│              │  ← Seller, Return │            │ → Order,Payment │
   │  → WebSocket GW │              │    (Conformist)   │            │   Customer, Cart│
   └─────────────────┘              └───────────────────┘            └─────────────────┘
```

---

## Bảng quan hệ đầy đủ

| Upstream (U)  | Downstream (D)                    | Pattern            | Protocol       | Ghi chú                                                      |
|---------------|-----------------------------------|--------------------|----------------|--------------------------------------------------------------|
| IAM           | Tất cả services                   | Conformist         | REST (token)   | Mọi service đều phụ thuộc IAM, không thể thay đổi IAM model  |
| Catalog       | Inventory                         | Customer-Supplier  | Kafka (OHS/PL) | Inventory nhận SKU definition từ Catalog                     |
| Catalog       | Search                            | OHS/PL → ACL       | Kafka          | Search tự transform sang read model của mình                 |
| Cart          | Order                             | Customer-Supplier  | Kafka          | Order nhận cart snapshot khi checkout                        |
| Order         | Inventory                         | Partnership (Saga) | Kafka          | Choreography Saga — hai bên phụ thuộc lẫn nhau               |
| Order         | Payment                           | Customer-Supplier  | Kafka (Saga)   | Order trigger payment sau khi inventory reserved             |
| Payment       | Order + Inventory                 | Customer-Supplier  | Kafka (Saga)   | Compensate cả hai khi payment fail                           |
| Order         | Fulfillment                       | Customer-Supplier  | Kafka          | Fulfillment nhận lệnh sau PaymentSucceeded                   |
| Pricing       | Fulfillment                       | OHS (gRPC)         | gRPC           | Fulfillment gọi Pricing để tính phí + phân công              |
| Shipper       | Fulfillment                       | Customer-Supplier  | Kafka / query  | Fulfillment đọc availability từ Shipper BC                   |
| Order         | Review                            | OHS/PL             | Kafka          | OrderCompleted event unlock quyền review                     |
| Promotion     | Order                             | OHS                | REST / Redis   | Order gọi Promotion để validate voucher                      |
| Seller        | Workflow                          | Conformist         | Temporal SDK   | Seller conform theo Temporal workflow API                    |
| Return        | Workflow                          | Conformist         | Temporal SDK   | Return conform theo Temporal workflow API                    |
| Domain events | Notification                      | OHS/PL             | Kafka          | Notification là consumer thụ động, không ảnh hưởng publisher |
| Notification  | WebSocket Gateway                 | Customer-Supplier  | Redis Pub/Sub  | Notification delegate real-time delivery                     |
| Scheduler     | Order / Payment / Customer / Cart | Customer-Supplier  | Kafka          | Scheduler là trigger — downstream không biết về Scheduler    |

---

## Generic BCs — vai trò đặc biệt

Ba BC dưới đây là **shared infrastructure** — không thuộc luồng nghiệp vụ cụ thể nào, được tất cả BC khác sử dụng:

| BC               | Vai trò trong Context Map                                                                     |
|------------------|-----------------------------------------------------------------------------------------------|
| **IAM**          | Pure upstream — mọi service conform, không BC nào có quyền thay đổi IAM model                 |
| **Notification** | Pure downstream aggregator — nhận event từ tất cả BC, không BC nào phụ thuộc vào Notification |
| **Search**       | Pure downstream projection — nhận event, dùng ACL để giữ read model độc lập với upstream      |

---

## Integration Patterns — chú giải

| Pattern           | Ý nghĩa                                                                                    |
|-------------------|--------------------------------------------------------------------------------------------|
| Conformist        | Downstream chấp nhận hoàn toàn model của upstream, không có ACL                            |
| Customer-Supplier | Upstream (supplier) và downstream (customer) phối hợp — downstream có thể yêu cầu thay đổi |
| Partnership       | Hai BC phát triển cùng nhau, thay đổi phải được phối hợp (Order + Inventory trong Saga)    |
| OHS/PL            | Upstream cung cấp giao diện chuẩn (Kafka+Avro), downstream dùng trực tiếp                  |
| ACL               | Downstream tạo lớp chuyển đổi để bảo vệ domain model của mình khỏi upstream model          |
