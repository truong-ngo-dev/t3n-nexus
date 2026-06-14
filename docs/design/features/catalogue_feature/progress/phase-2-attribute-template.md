# Phase 2 — AttributeTemplate

**Status:** TODO  
**Started:** —  
**Completed:** —

## Checklist

### Domain
- [ ] `AttributeTemplateId`, `AttributeOptionId` — typed UUID wrappers
- [ ] `AttributeScope` enum: `GLOBAL | CATEGORY`
- [ ] `InputType` enum: `SELECT | TEXT | NUMBER | BOOLEAN`
- [ ] `AttributeOptionStatus` enum: `ACTIVE | INACTIVE`
- [ ] `AttributeOption` entity — `{ optionId, value, displayValue, status }`
- [ ] `AttributeTemplate` aggregate root — `{ templateId, name, displayName, inputType, scope, options: List<AttributeOption> }`
- [ ] `AttributeTemplate.create(id, name, displayName, inputType, scope)` static factory
- [ ] `AttributeTemplate.updateDisplayName(displayName)` — chỉ cho phép sửa displayName, không sửa `name` hoặc `inputType`
- [ ] `AttributeTemplate.addOption(optionId, value, displayValue)` — throw nếu `inputType != SELECT`
- [ ] `AttributeTemplate.deactivateOption(optionId)` — soft delete, throw nếu option không tồn tại
- [ ] `AttributeTemplateErrorCode` enum — `TEMPLATE_NOT_FOUND(404)`, `TEMPLATE_NAME_EXISTS(409)`, `OPTION_NOT_FOUND(404)`, `OPTION_IN_USE(409)`, `INPUT_TYPE_NOT_SELECT(422)`
- [ ] `AttributeTemplateRepository` interface — `findById`, `existsByName`, `save`, `findAll`
- [ ] `AttributeTemplateDomainService` — `validateOptionNotUsedByVariant(optionId)` — inject `VariantRepository` để check `existsByOptionId`; gọi trước `deactivateOption`

### Infrastructure — Persistence
- [ ] `AttributeTemplateJpaEntity`, `AttributeOptionJpaEntity`
- [ ] `AttributeTemplateJpaRepository` — query `findAllByScope(scope)`
- [ ] `AttributeOptionJpaRepository` — query `existsByIdAndVariantCombinationItems_optionId(optionId)` (join sang `variant_combination_item`)
- [ ] `AttributeTemplateMapper` — map cả options list
- [ ] `AttributeTemplatePersistenceAdapter implements AttributeTemplateRepository`

### Application — Commands
- [ ] `CreateAttributeTemplate` — `Command(name, displayName, inputType, scope)`, `Result(UUID id)`, `Handler`  
      check name unique → `AttributeTemplate.create()` → save
- [ ] `UpdateAttributeTemplate` — `Command(UUID id, displayName)`, `Result`, `Handler`
- [ ] `AddAttributeOption` — `Command(UUID templateId, value, displayValue)`, `Result(UUID optionId)`, `Handler`
- [ ] `DeactivateAttributeOption` — `Command(UUID templateId, UUID optionId)`, `Result`, `Handler`  
      **⚠️ Guard**: gọi `AttributeTemplateDomainService.validateOptionNotUsedByVariant()` trước khi deactivate

### Application — Queries
- [ ] `ListAttributeTemplates` — `Query()`, `Result(List<AttributeTemplateDetail>)`, `Handler`  
      `AttributeTemplateDetail` gồm full info kể cả options list

### Presentation
- [ ] `presentation/attribute_template/model/`: `CreateAttributeTemplateRequest`, `AddAttributeOptionRequest`, `AttributeTemplateResponse`, `AttributeOptionResponse`
- [ ] `AttributeTemplateController`:
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
