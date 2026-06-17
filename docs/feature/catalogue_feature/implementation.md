# Catalog Service — Implementation Plan

**Service:** `catalog-service`  
**Domain:** Core Domain  
**Design refs:** `docs/design/services/catalog-service.md`, `docs/architecture/catalog-sku-spu.md`

---

## Phase Overview

| Phase | Tên                    | Nội dung                                                              | Est. sessions |
|-------|------------------------|-----------------------------------------------------------------------|---------------|
| 0     | Bootstrap              | Module setup, Flyway DDL, base config                                 | 1             |
| 1     | Brand                  | Full stack — domain → infra → app → presentation                      | 1             |
| 2     | AttributeTemplate      | Full stack — bao gồm AttributeOption + soft-delete guard              | 1             |
| 3     | Category               | Full stack — closure table, attribute assignment merge                | 2             |
| 4     | Product Domain + Infra | Aggregate + persistence (Variant, Image, VO) — phức tạp nhất          | 2             |
| 5     | Product Write Side     | Command handlers + presentation (create, update, publish, image)      | 2             |
| 6     | Product Read + Cache   | Query handlers, Caffeine L1 + Redis L2, pub/sub invalidation          | 1             |
| 7     | Variant                | Full stack — Variant commands, price change, deactivate               | 1             |
| 8     | Events + Outbox        | Outbox wiring, Kafka topics, all domain event → EventEnvelope mapping | 1             |

**Dependency order:** Phase 0 → 1 → 2 → 3 → 4 → 5, 6, 7 (parallel) → 8

---

## Cross-Cutting Concerns

Những thứ implement 1 lần, dùng xuyên suốt — không lặp lại trong từng phase:

- **DDD convention**: `docs/convention/ddd-structure.md` — bắt buộc, không thương lượng
- **Libs dùng**: `common-domain` (AggregateRoot, Money), `outbox-starter`, `common-events` (EventEnvelope), `web-commons` (ApiResponse), `observability-starter`
- **No var**: khai báo type tường minh, không dùng `var`
- **Jackson**: `com.fasterxml.jackson.annotation.*` vẫn dùng bình thường
- **File separation**: không áp dụng (backend Java, không phải Angular)
- **Event dispatch**: luôn sau `repository.save()` — không dispatch trước persist
- **Domain event**: aggregate raise, không phải handler
- **CommandHandler**: trả `Result`, không trả `Void`

---

## Kiến Trúc Cache (Catalog-Specific)

Caffeine L1 + Redis L2, Redis pub/sub broadcast L1 invalidation cross-instance.

```
key prefix: catalog:{entity}:{id}
catalog:product:{id}              TTL L1=2m, L2=10m
catalog:product:{id}:variants     TTL L1=2m, L2=10m
catalog:category:tree             TTL L1=30m, L2=1h
catalog:categories:{id}:attributes TTL L1=30m, L2=1h
```

Invalidation event: `catalog:cache:invalidate` (Redis pub/sub channel).

---

## Progress Files

- [Phase 0](progress/phase-0-bootstrap.md)
- [Phase 1](progress/phase-1-brand.md)
- [Phase 2](progress/phase-2-attribute-template.md)
- [Phase 3](progress/phase-3-category.md)
- [Phase 4](progress/phase-4-product-domain.md)
- [Phase 5](progress/phase-5-product-write.md)
- [Phase 6](progress/phase-6-product-read-cache.md)
- [Phase 7](progress/phase-7-variant.md)
- [Phase 8](progress/phase-8-events.md)
