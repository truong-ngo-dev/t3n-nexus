# Phase 0 — Bootstrap

**Status:** DONE  
**Started:** 2026-06-13  
**Completed:** 2026-06-13

## Checklist

### Module Setup
- [x] Tạo Maven module `catalog-service` trong `services/`, khai báo trong root `pom.xml`
- [x] `pom.xml`: parent = project BOM, packaging = jar
- [x] Dependencies: `common-domain`, `outbox-starter`, `common-events`, `common-web`, `observability-starter`, `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-data-redis`, `caffeine`, `minio`, `spring-kafka`, `flyway-core`
- [x] `CatalogServiceApplication.java` tại `vn.t3nexus.catalog`

### Config
- [x] `application.properties`: datasource (PostgreSQL port 5435/catalog_db), redis, minio endpoint/bucket, kafka bootstrap-servers, outbox strategy=CDC
- [x] `application-local.properties`: local overrides cho dev (SQL logging, plain credentials)
- [x] `infrastructure/crosscutting/config/EventDispatcherConfig.java` — wire `EventDispatcher` bean từ `common-domain`

### Flyway DDL — V1__init_catalog_schema.sql
- [x] `brand(id UUID PK, name, slug UNIQUE, status, created_at, updated_at)`
- [x] `attribute_template(id UUID PK, name UNIQUE, display_name, input_type, scope, created_at, updated_at)`
- [x] `attribute_option(id UUID PK, template_id FK, value, display_value, status, created_at)`
- [x] `category(id UUID PK, name, slug UNIQUE, parent_id FK nullable, level SMALLINT, image_url, status, created_at, updated_at)`
- [x] `category_closure(ancestor_id UUID FK, descendant_id UUID FK, depth INT, PK(ancestor_id, descendant_id))`  
      Index: `idx_closure_ancestor(ancestor_id)`, `idx_closure_descendant(descendant_id)`
- [x] `category_attribute_assignment(category_id UUID FK, template_id UUID FK, is_variant_defining, is_required, is_filterable, display_order, PK(category_id, template_id))`
- [x] `product(id UUID PK, seller_id UUID, category_id UUID FK, brand_id UUID FK, name, description TEXT, status, warranty_months INT, warranty_type, warranty_coverage, created_at, updated_at)`  
      Index: `idx_product_seller(seller_id)`, `idx_product_category(category_id)`, `idx_product_status(status)`
- [x] `product_attribute_value(product_id UUID FK, template_id UUID FK, value, PK(product_id, template_id))`
- [x] `product_image(id UUID PK, product_id UUID FK, object_key, display_order INT, created_at)`  
      Index: `idx_product_image_product(product_id)`
- [x] `variant(id UUID PK, product_id UUID FK, combination_hash VARCHAR(64), price BIGINT, original_price BIGINT nullable, weight DECIMAL, length DECIMAL, width DECIMAL, height DECIMAL, barcode, status, created_at, updated_at)`  
      Unique: `uq_variant_combination(product_id, combination_hash)`  
      Index: `idx_variant_product(product_id)`
- [x] `variant_combination_item(variant_id UUID FK, template_id UUID FK, option_id UUID FK, PK(variant_id, template_id))`
- [x] `variant_image(id UUID PK, variant_id UUID FK, object_key, display_order INT)`
- [x] `outbox_events` — schema đầy đủ (id, event_id, aggregate_type, aggregate_id VARCHAR(36), event_type, routing_key, payload, occurred_on, created_at, trace_id, span_id)

## Verify

Flyway migration chạy thành công, không có lỗi:
```
SHOW TABLES IN catalog_db;
-- Expect: 13 tables xuất hiện
```

`GET /actuator/health` trả về `{ "status": "UP" }`.

## Session Log

### 2026-06-13
- Tạo `services/catalog-service/pom.xml` — dependencies đầy đủ theo checklist + MinIO transitive deps (okhttp-jvm, okio)
- Đăng ký module trong `services/pom.xml`
- `CatalogServiceApplication.java` tại `vn.t3nexus.catalog` (theo convention project thay vì package cũ trong doc)
- `application.properties`: port 8005, DB catalog_db port 5435, Redis, MinIO, Kafka producer
- `application-local.properties`: SQL debug logging, plain credentials
- `EventDispatcherConfig.java`: wire `EventDispatcher` bean
- `V1__init_catalog_schema.sql`: 13 tables đầy đủ với constraints, indexes, check constraints
- `outbox_events` schema dùng phiên bản mới nhất (routing_key + trace/span columns) — không cần alter migrations về sau
