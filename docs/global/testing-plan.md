# Integration Testing Plan

> Mục đích: verify correctness của các pattern kỹ thuật được áp dụng trong hệ thống.
> Đây **không phải load test** — con số local đủ nhỏ để chạy trên máy dev nhưng validate đúng behavior.
> Pattern được validate là như nhau — correctness không phụ thuộc vào scale.

---

## 1. Nguyên Tắc

- Tất cả đều là **integration test** — cần infrastructure thực, không mock DB hay message broker
- `simulator-service` là công cụ chính để inject concurrency, failure, và simulate external actors
- Mỗi scenario định nghĩa: **verify gì** + **local setup** để reproduce

---

## 2. Concurrency & Race Condition

Mục tiêu: verify atomicity và consistency dưới concurrent load.

| Scenario                      | Verify                                                   | Local load             | Services cần bật                           |
|-------------------------------|----------------------------------------------------------|------------------------|--------------------------------------------|
| Hot-SKU contention            | Không oversell — stock deducted đúng số lượng, không âm  | 200 concurrent / 1 SKU | inventory, order, Redis, Kafka, PostgreSQL |
| Voucher race condition        | Mỗi voucher chỉ redeem được 1 lần dù N request đồng thời | 50 concurrent          | promotion, Redis, PostgreSQL               |
| Mua limited offer (inventory) | Stock reservation atomic, không duplicate reservation    | 200 concurrent / 1 SKU | inventory, Redis, Kafka, PostgreSQL        |

**Verify criteria cho Hot-SKU (Flash Sale pattern):**

| Pattern                 | Verify                                                        |
|-------------------------|---------------------------------------------------------------|
| Bloom Filter            | Sold-out check không hit Redis khi filter positive            |
| Redis atomic DECR + Lua | Stock không bao giờ < 0 sau N concurrent DECR                 |
| Kafka queue             | Order events được xử lý tuần tự per-partition, không skip     |
| Rate limiting           | Checkout RPS tại api-gateway không vượt threshold đã cấu hình |

---

## 3. Saga & Distributed Transaction

Mục tiêu: verify compensating transaction rollback đúng và đủ ở mọi failure point.

| Scenario                   | Verify                                                        | Local setup                                      | Services cần bật                             |
|----------------------------|---------------------------------------------------------------|--------------------------------------------------|----------------------------------------------|
| Tạo đơn hàng → reservation | Stock bị reserve ngay sau OrderCreated                        | —                                                | order, inventory, Kafka, PostgreSQL          |
| Payment failure → rollback | Order cancelled + stock released đầy đủ                       | Inject failure tại mỗi bước, 20 concurrent flows | order, inventory, payment, Kafka, PostgreSQL |
| Batch payout cho seller    | Mỗi payout chỉ được xử lý đúng 1 lần (idempotency via Outbox) | —                                                | payment, scheduler, PostgreSQL, Kafka        |

---

## 4. Real-time & Streaming

Mục tiêu: verify event được deliver đến đúng client trong latency SLA.

| Scenario                               | Verify                                                | Local setup                              | Services cần bật                                 | SLA                |
|----------------------------------------|-------------------------------------------------------|------------------------------------------|--------------------------------------------------|--------------------|
| Shipper gửi location update            | Location update xuất hiện trên browser trong < 500ms  | 5 simulated shippers, update 1s/interval | fulfillment, shipper, EMQX                       | < 500ms end-to-end |
| Theo dõi giao hàng realtime (customer) | WebSocket push nhận được khi shipment status thay đổi | —                                        | fulfillment, websocket-gateway, Redis            | < 1s               |
| In-app notification                    | Notification đến browser sau event phát sinh          | 500 events / 10s                         | notification, websocket-gateway, Redis, Kafka    | < 1s end-to-end    |

---

## 5. Scheduled Jobs & Automation

Mục tiêu: verify job trigger đúng điều kiện, không trùng lặp, có thể trigger thủ công qua simulator.

| Scenario                                | Verify                                             | Trigger                    |
|-----------------------------------------|----------------------------------------------------|----------------------------|
| Auto-cancel đơn prepaid chưa thanh toán | Order chuyển CANCELLED sau timeout, stock released | Cron hoặc simulator bypass |
| Batch payout                            | Payout chạy đúng 1 lần/batch cycle, idempotent     | Cron hoặc simulator bypass |
| Hết hạn điểm thưởng                     | Points expired đúng ngày, FIFO deducted trước      | Cron hoặc simulator bypass |
| Dọn dẹp giỏ hàng bỏ quên                | Cart cũ bị xóa sau TTL                             | Cron hoặc simulator bypass |

---

## 6. Business Logic & Workflow

Mục tiêu: verify correctness của domain logic và multi-step workflow — không cần concurrency.

| Scenario                     | Verify                                                            | Services cần bật              |
|------------------------------|-------------------------------------------------------------------|-------------------------------|
| Lịch sử trạng thái đơn hàng  | Event Sourcing replay ra đúng state sequence                      | order, PostgreSQL, Kafka      |
| Lịch sử di chuyển hàng hoá   | Warehouse movement events tái tạo đúng vị trí hàng                | warehouse, PostgreSQL, Kafka  |
| Seller onboarding            | Temporal workflow hoàn thành đúng các bước, compensate khi reject | seller, workflow, Kafka       |
| Hoàn hàng multi-step         | Return flow đi đúng các bước, dispute escalate khi cần            | return, workflow, Kafka       |
| Tính phí vận chuyển          | Drools rule trả ra đúng fee theo zone + weight                    | pricing (gRPC)                |
| Tự động phân công shipper    | Rule Engine chọn đúng shipper theo availability + zone            | fulfillment, pricing, shipper |
| Tìm kiếm sản phẩm có filter  | Full-text + faceted search trả kết quả đúng, latency < 200ms      | search, Elasticsearch         |
| Pipeline phân tích doanh thu | Flink/Spark aggregate đúng số liệu vào DWH                        | reporting, Kafka, DWH         |

---

## 7. Infrastructure Checklist

Mỗi lần chạy test cần đảm bảo:

- [ ] Kafka broker + Zookeeper up, topics đã tạo
- [ ] Redis standalone (hoặc cluster) up
- [ ] PostgreSQL up, schema migrated
- [ ] EMQX broker up (cho MQTT scenarios)
- [ ] `simulator-service` deployed ở môi trường dev/staging
- [ ] Temporal server up (cho workflow scenarios)
- [ ] Elasticsearch up (cho search scenarios)
