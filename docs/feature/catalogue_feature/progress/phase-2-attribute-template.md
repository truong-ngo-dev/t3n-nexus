# Phase 2 — AttributeTemplate

**Status:** DONE  
**Started:** 2026-06-14  
**Completed:** 2026-06-15

## Checklist

### Domain
- [x] `AttributeTemplateId`, `AttributeOptionId` — typed UUID wrappers
- [x] `AttributeScope` enum: `GLOBAL | CATEGORY`
- [x] `InputType` enum: `SELECT | TEXT | NUMBER | BOOLEAN`
- [x] `AttributeOptionStatus` enum: `ACTIVE | INACTIVE`
- [x] `AttributeOption` entity — `{ optionId, value, displayValue, status }`
- [x] `AttributeTemplate` aggregate root — `{ templateId, name, displayName, inputType, scope, options: List<AttributeOption> }`
- [x] `AttributeTemplate.create(id, name, displayName, inputType, scope)` static factory
- [x] `AttributeTemplate.updateDisplayName(displayName)` — chỉ cho phép sửa displayName, không sửa `name` hoặc `inputType`
- [x] `AttributeTemplate.addOption(optionId, value, displayValue)` — throw nếu `inputType != SELECT`
- [x] `AttributeTemplate.deactivateOption(optionId)` — soft delete, throw nếu option không tồn tại
- [x] `AttributeTemplateErrorCode` enum — `TEMPLATE_NOT_FOUND(404)`, `TEMPLATE_NAME_EXISTS(409)`, `OPTION_NOT_FOUND(404)`, `OPTION_IN_USE(409)`, `INPUT_TYPE_NOT_SELECT(422)`
- [x] `AttributeTemplateRepository` interface — `findById`, `existsByName`, `save`, `findAll`
- [x] `AttributeTemplateDomainService` — `validateOptionNotUsedByVariant(optionId)` — inject `VariantRepository` để check `existsByOptionId`; gọi trước `deactivateOption`

### Infrastructure — Persistence
- [x] `AttributeTemplateJpaEntity`, `AttributeOptionJpaEntity`
- [x] `AttributeTemplateJpaRepository` — query `findAllByScope(scope)`
- [x] `VariantCombinationItemJpaEntity` + `VariantCombinationItemJpaRepository` — `existsByOptionId` (stub; Phase 4 mở rộng)
- [x] `AttributeTemplateMapper` — map cả options list
- [x] `AttributeTemplatePersistenceAdapter implements AttributeTemplateRepository`
- [x] `VariantPersistenceAdapter implements VariantRepository`

### Application — Commands
- [x] `CreateAttributeTemplate` — `Command(name, displayName, inputType, scope)`, `Result(UUID id)`, `Handler`  
      check name unique → `AttributeTemplate.create()` → save
- [x] `UpdateAttributeTemplate` — `Command(UUID id, displayName)`, `Result`, `Handler`
- [x] `AddAttributeOption` — `Command(UUID templateId, value, displayValue)`, `Result(UUID optionId)`, `Handler`
- [x] `DeactivateAttributeOption` — `Command(UUID templateId, UUID optionId)`, `Result`, `Handler`  
      **⚠️ Guard**: gọi `AttributeTemplateDomainService.validateOptionNotUsedByVariant()` trước khi deactivate

### Application — Queries
- [x] `ListAttributeTemplates` — `Query()`, `Result(List<AttributeTemplateDetail>)`, `Handler`  
      `AttributeTemplateDetail` gồm full info kể cả options list

### Presentation
- [x] `presentation/attributetemplate/model/`: `CreateAttributeTemplateRequest`, `UpdateAttributeTemplateRequest`, `AddAttributeOptionRequest`, `AttributeTemplateResponse`, `AttributeOptionResponse`
- [x] `AttributeTemplateController`:
  - `GET /api/admin/attribute-templates` → `ListAttributeTemplates`
  - `POST /api/admin/attribute-templates` → `CreateAttributeTemplate`
  - `PUT /api/admin/attribute-templates/{id}` → `UpdateAttributeTemplate`
  - `POST /api/admin/attribute-templates/{id}/options` → `AddAttributeOption`
  - `DELETE /api/admin/attribute-templates/{id}/options/{optionId}` → `DeactivateAttributeOption`

## Verify

```bash
# Tạo GLOBAL template (text)
POST /api/admin/attribute-templates
{ "name": "brand_text", "displayName": "Thương hiệu", "inputType": "TEXT", "scope": "GLOBAL" }
# → 201

# Tạo CATEGORY template (select) + options
POST /api/admin/attribute-templates
{ "name": "color", "displayName": "Màu sắc", "inputType": "SELECT", "scope": "CATEGORY" }
# → 201, lấy id

POST /api/admin/attribute-templates/{id}/options
{ "value": "black", "displayValue": "Đen" }
# → 201

# Add option cho TEXT template → 422
POST /api/admin/attribute-templates/{textId}/options
{ "value": "x" }
# → 422

# List
GET /api/admin/attribute-templates
# → 200, data = 2 templates, color template có options: [{ value: "black" }]
```

## Session Log

### 2026-06-15 — API doc
- Tạo `docs/service/catalog-service/api.yaml` — OpenAPI 3.0 spec cho Brand (Phase 1) + AttributeTemplate (Phase 2)
- 9 endpoints, full schema, error responses (400/404/409/422), business rule notes inline

### 2026-06-15 — Presentation layer
- `CreateAttributeTemplateRequest`, `UpdateAttributeTemplateRequest`, `AddAttributeOptionRequest`
- `AttributeTemplateResponse`, `AttributeOptionResponse`
- `AttributeTemplateController` — 5 endpoints
- `addOption` response: `status=null` (newly created, caller biết là ACTIVE)

### 2026-06-15 — Application layer
- `CreateAttributeTemplate` — không evict cache (template mới chưa assign vào category nào)
- `UpdateAttributeTemplate`, `AddAttributeOption`, `DeactivateAttributeOption` — `@CacheEvict(CATEGORY_ATTRIBUTES, allEntries=true)` vì không biết category nào reference template này
- `ListAttributeTemplates` — không cache (Admin-only, không phải hot path)
- `DeactivateAttributeOption` — validate trước `findById`: guard domain service trước khi load AR

### 2026-06-15 — Infrastructure layer
- `AttributeOption` domain: thêm `createdAt` field + cập nhật `reconstitute()`
- `AttributeTemplateJpaEntity` — `@OneToMany options` với `FetchType.EAGER` (options luôn cần cùng template)
- `AttributeOptionJpaEntity` — `@ManyToOne template`, `updatable = false` trên FK
- `AttributeTemplateJpaRepository` — `existsByName`, `findAllByScope` (dùng cho Phase 3)
- `AttributeTemplateMapper` — map bidirectional: set `template` ref trên từng option entity
- `VariantCombinationItemJpaEntity` + `Key` + `Repository` — stub tại `persistence/variant/`
- `AttributeTemplatePersistenceAdapter`, `VariantPersistenceAdapter`

### 2026-06-14
- `AttributeScope`, `InputType`, `AttributeOptionStatus` enums
- `AttributeTemplateErrorCode` — codes 20101–20105
- `AttributeOption` entity — `deactivate()` package-private (chỉ AR gọi)
- `AttributeTemplate` AR — `name` và `inputType` là `final` (không thể sửa sau khi tạo)
- `AttributeTemplateRepository` interface
- `VariantRepository` stub tại `domain/variant/` — chỉ `existsByOptionId`; Phase 4 sẽ mở rộng
- `AttributeTemplateDomainService` — inject `VariantRepository`, throw `OPTION_IN_USE`
