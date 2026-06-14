# Phase 8 — Events + Outbox

**Status:** TODO  
**Started:** —  
**Completed:** —

## Checklist

### Outbox Wiring

- [ ] Verify `outbox-starter` đã được config đúng strategy (`CDC` preferred)
- [ ] `OutboxEventStore` bean từ `outbox-starter` được inject vào `EventDispatcher` config  
      Mỗi domain event dispatch → `OutboxEventStore.store(eventEnvelope)` → persist vào `outbox_event` table trong cùng transaction với aggregate save
- [ ] Debezium connector config cho `catalog-service` outbox table (hoặc polling scheduler nếu dùng POLLING strategy)

### Kafka Topics — khai báo

- [ ] `catalog.product.published`
- [ ] `catalog.product.unpublished`
- [ ] `catalog.product.blocked`
- [ ] `catalog.product.unblocked`
- [ ] `catalog.product.updated`
- [ ] `catalog.variant.price-changed`
- [ ] `catalog.variant.deactivated`
- [ ] `catalog.category.updated`

### Domain Event → EventEnvelope Mapping

Mỗi domain event cần một mapper chuyển sang `EventEnvelope` với payload đúng schema.  
Đặt trong `infrastructure/messaging/` hoặc trong `outbox-starter` hook.

- [ ] `ProductPublishedEvent` → topic `catalog.product.published`  
      Payload: `{ productId, sellerId, categoryId, skuIds: [], name, brandName }`
- [ ] `ProductUnpublishedEvent` → topic `catalog.product.unpublished`  
      Payload: `{ productId, sellerId }`
- [ ] `ProductBlockedEvent` → topic `catalog.product.blocked`  
      Payload: `{ productId, sellerId, reason }`
- [ ] `ProductUnblockedEvent` → topic `catalog.product.unblocked`  
      Payload: `{ productId }`
- [ ] `ProductUpdatedEvent` → topic `catalog.product.updated`  
      Payload: `{ productId }`
- [ ] `VariantPriceChangedEvent` → topic `catalog.variant.price-changed`  
      Payload: `{ skuId, productId, newPrice: { amount, currency } }`
- [ ] `VariantDeactivatedEvent` → topic `catalog.variant.deactivated`  
      Payload: `{ skuId, productId }`
- [ ] `CategoryUpdatedEvent` → topic `catalog.category.updated`  
      Payload: `{ categoryId }`

### EventEnvelope Schema

Tất cả events wrap trong `EventEnvelope` từ `common-events`:
```json
{
  "eventId": "ULID",
  "eventType": "ProductPublishedEvent",
  "sourceService": "catalog-service",
  "correlationId": "...",
  "traceId": "...",
  "occurredAt": "2026-06-13T...",
  "schemaVersion": 1,
  "payload": { ... }
}
```

- [ ] Verify `schemaVersion=1` cho tất cả events trong phase này
- [ ] Register schemas tại Confluent Schema Registry (nếu dùng)

## Verify

```bash
# Trigger ProductPublishedEvent
POST /api/seller/products/{id}/publish

# Verify outbox table ngay sau publish
SELECT * FROM outbox_event WHERE aggregate_id = '{productId}' AND event_type = 'ProductPublishedEvent';
# → 1 row, processed = false (chưa được CDC pick up)

# Sau khi CDC/polling chạy:
# Message xuất hiện trong Kafka topic
kafka-console-consumer --topic catalog.product.published --from-beginning
# → EventEnvelope JSON với payload đầy đủ

# End-to-end: verify inventory-service nhận ProductPublishedEvent
# → StockRecord tạo cho mỗi skuId trong event
SELECT * FROM stock_record WHERE sku_id IN (SELECT id FROM variant WHERE product_id = '{productId}');
# → rows tương ứng với số Variant ACTIVE

# Verify VariantPriceChangedEvent
PUT /api/seller/products/{productId}/variants/{skuId} { "price": 1000 }
SELECT * FROM outbox_event WHERE event_type = 'VariantPriceChangedEvent';
# → 1 row
```

## Session Log
