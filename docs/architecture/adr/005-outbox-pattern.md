# ADR-005 — Transactional Outbox cho Kafka Publish

**Status:** Accepted

## Context

Publish Kafka event sau khi write DB có nguy cơ partial failure: DB commit thành công nhưng Kafka publish fail (hoặc ngược lại). Trong Saga choreography, mất event = saga bị stuck. Cần đảm bảo at-least-once delivery mà không dùng distributed transaction (2PC quá nặng).

## Decision

**Transactional Outbox Pattern** bắt buộc trên mọi service publish Kafka event:

1. Ghi domain event vào bảng `outbox_events` trong **cùng DB transaction** với business write
2. **Debezium** đọc MySQL binlog, detect rows mới trong `outbox_events`, push lên Kafka
3. Consumer **dedup theo `eventId`** — idempotency bắt buộc (at-least-once delivery)

Managed bởi `outbox-starter` shared lib — service annotate `@OutboxPublish`, không tự ghi outbox table.

Cleanup: scheduler-service chạy job định kỳ xóa outbox rows đã processed.

## Consequences

**+** Atomicity đảm bảo — không bao giờ mất event nếu DB commit thành công  
**+** Debezium đọc binlog không add write latency vào hot path  
**+** Consistent pattern across mọi service — không có ngoại lệ  
**−** Cần Debezium connector riêng cho từng MySQL schema  
**−** Consumer bắt buộc implement idempotency — at-least-once, không phải exactly-once  
**−** Outbox table cần cleanup job để không phình vô hạn
