# Phase 3 — Category

**Status:** DONE  
**Started:** 2026-06-15  
**Completed:** 2026-06-15

## Checklist

### Domain
- [x] `CategoryId` — typed UUID wrapper
- [x] `CategoryLevel` enum: `L1(1) | L2(2) | L3(3)` — có `nextLevel()` throw `MAX_DEPTH_EXCEEDED` nếu gọi trên L3
- [x] `CategoryStatus` enum: `ACTIVE | INACTIVE`
- [x] `CategoryAttributeAssignment` value object — `{ attributeTemplateId, isVariantDefining, isRequired, isFilterable, displayOrder }`. Equals/hashCode trên `attributeTemplateId` duy nhất
- [x] `CategoryErrorCode` enum — codes 20201–20207
- [x] `CategoryUpdatedEvent` domain event — eventId tự generate bằng `UUID.randomUUID()` (ULIDGenerator là Spring bean, không inject vào domain)
- [x] `Category` aggregate root — `{ categoryId, name, slug, parentId, level, imageUrl, status, assignments: List<CategoryAttributeAssignment> }`
- [x] `Category.createRoot(id, name, slug)` static factory — level=L1, parentId=null
- [x] `Category.createChild(id, name, slug, parentId, parentLevel)` static factory — gọi `parentLevel.nextLevel()` → tự throw `MAX_DEPTH_EXCEEDED` nếu L3
- [x] `Category.update(name, imageUrl)` → raise `CategoryUpdatedEvent`
- [x] `Category.assignAttribute(assignment)` — **guard**: duplicate `attributeTemplateId` → throw `ASSIGNMENT_ALREADY_EXISTS`
- [x] `Category.updateAssignment(assignment)` — tìm theo `attributeTemplateId`, replace toàn bộ config
- [x] `Category.removeAssignment(templateId)` — throw `ASSIGNMENT_NOT_FOUND` nếu không tồn tại
- [x] `CategoryRepository` interface — `findById`, `existsBySlug`, `existsByParentId(categoryId)`, `hasProductReference(categoryId)`, `save`, `findRoots()`

### Infrastructure — Persistence
- [x] `CategoryJpaEntity` — không có `@OneToMany children`; tree navigate qua closure table; `level` lưu là `int`
- [x] `CategoryClosureKey` — Serializable composite key (ancestorId, descendantId)
- [x] `CategoryClosureJpaEntity` — `@IdClass(CategoryClosureKey.class)`
- [x] `CategoryAssignmentKey` — Serializable composite key (categoryId, templateId)
- [x] `CategoryAttributeAssignmentJpaEntity` — `@IdClass(CategoryAssignmentKey.class)`; quản lý riêng (không cascade từ Category)
- [x] `CategoryJpaRepository` — `existsBySlug`, `existsByParentId`
- [x] `CategoryClosureJpaRepository` — `findByAncestorId`, `findByDescendantId`, `countByAncestorIdAndDepth`, `insertSelfReference`, `insertAncestorReferences` (native SQL `INSERT INTO ... SELECT`)
- [x] `CategoryAttributeAssignmentJpaRepository` — `findByCategoryId`, `deleteByCategoryId`
- [x] `CategoryMapper` — `toDomain(entity, assignments)`, `toJpaEntity`, `toAssignmentEntities`; int↔CategoryLevel conversion qua switch
- [x] `CategoryPersistenceAdapter implements CategoryRepository`
  - `save()`: isNew check → jpa.save → assignment sync (delete+reinsert) → closure insert nếu mới
  - `findAll()`: 2 queries + groupBy để tránh N+1
  - `hasProductReference()`: stub return false — TODO Phase 4

### Application — Queries (implement trước Commands để verify tree)
- [x] `GetCategoryTree` — `@Cacheable(CATEGORY_TREE, key="'all'")`, build tree in-memory: group by parentId → wire children → return L1 roots sorted by name
- [x] `GetCategoryAttributes` — `@Cacheable(CATEGORY_ATTRIBUTES, key=#categoryId)`, merge GLOBAL (displayOrder=-1, sorted first) + CATEGORY assignments (sorted by displayOrder)

### Application — Commands
- [x] `CreateCategory` — `@CacheEvict(CATEGORY_TREE)` + `publisher.clear(CATEGORY_TREE)`; parentId nullable → createRoot/createChild
- [x] `UpdateCategory` — `@CacheEvict(CATEGORY_TREE)` + `publisher.clear(CATEGORY_TREE)`
- [x] `DeleteCategory` — `@CacheEvict(CATEGORY_TREE)` + `publisher.clear(CATEGORY_TREE)`; guards: HAS_CHILDREN, HAS_PRODUCT_REFERENCE; hard delete via `repository.delete(id)`
- [x] `AssignAttributeToCategory` — `@CacheEvict(CATEGORY_ATTRIBUTES, key=categoryId)`; validate template exists trước
- [x] `UpdateCategoryAttributeAssignment` — `@CacheEvict(CATEGORY_ATTRIBUTES, key=categoryId)`
- [x] `RemoveCategoryAttributeAssignment` — `@CacheEvict(CATEGORY_ATTRIBUTES, key=categoryId)`

### Presentation
- [x] `CreateCategoryRequest` — `@NotBlank name, slug`; `parentId` nullable
- [x] `UpdateCategoryRequest` — `@NotBlank name`; `imageUrl` nullable
- [x] `AssignAttributeRequest` — `@NotBlank templateId`; boolean flags + `displayOrder`
- [x] `UpdateAssignmentRequest` — boolean flags + `displayOrder` (templateId từ path)
- [x] `CategoryTreeResponse` — recursive record với `List<CategoryTreeResponse> children`
- [x] `CategoryAttributeResponse` — full DTO: templateId, name, displayName, inputType, scope, flags, displayOrder
- [x] `CategoryController` — 8 endpoints; `toTreeResponse()` recursive private helper
- [x] `docs/service/catalog-service/api.yaml` — thêm Category (Public) + Category (Admin) sections (8 endpoints, full schemas)

## Verify

```bash
# Tạo L1 → L2 → L3 hierarchy
POST /api/admin/categories { "name": "Electronics", "slug": "electronics" }
# → 201, id=L1_ID

POST /api/admin/categories { "name": "Phones", "slug": "phones", "parentId": "L1_ID" }
# → 201, id=L2_ID

POST /api/admin/categories { "name": "Smartphones", "slug": "smartphones", "parentId": "L2_ID" }
# → 201, id=L3_ID

# L4 không được phép
POST /api/admin/categories { "name": "Sub", "parentId": "L3_ID" }
# → 422

# Tree
GET /api/categories
# → nested: Electronics > Phones > Smartphones

# Assign Color template vào L3
POST /api/admin/categories/{L3_ID}/attributes
{ "templateId": "{colorTemplateId}", "isVariantDefining": true, ... }

# GetCategoryAttributes → GLOBAL + Color
GET /api/categories/{L3_ID}/attributes
# → [{ name: "brand_text", scope: "GLOBAL" }, { name: "color", scope: "CATEGORY", isVariantDefining: true }]

# Verify closure table
SELECT * FROM category_closure WHERE descendant_id = L3_ID;
# → 3 rows: (L1→L3 depth=2), (L2→L3 depth=1), (L3→L3 depth=0)
```

## Session Log

### 2026-06-15 — Presentation layer
- `CreateCategoryRequest`, `UpdateCategoryRequest`, `AssignAttributeRequest`, `UpdateAssignmentRequest`
- `CategoryTreeResponse` — recursive record
- `CategoryAttributeResponse` — full DTO
- `CategoryController` — 8 endpoints; private `toTreeResponse()` recursive mapper
- `api.yaml` — Category (Public) + Category (Admin) sections

### 2026-06-15 — Application layer
- `GetCategoryTree` — flat load → group by parentId → recursive buildNode (max depth 3); `@Cacheable(CATEGORY_TREE, key="'all'")`
- `GetCategoryAttributes` — GLOBAL via `findAllByScope(GLOBAL)` (thêm vào `AttributeTemplateRepository`); CATEGORY qua assignments; displayOrder=-1 cho GLOBAL → sort tự nhiên
- `CreateCategory`, `UpdateCategory`, `DeleteCategory` — `@CacheEvict(CATEGORY_TREE, allEntries=true)` + `publisher.clear(CATEGORY_TREE)` (L1+L2)
- Assignment handlers — `@CacheEvict(CATEGORY_ATTRIBUTES, key=categoryId)` (L2 only, no pub/sub)
- Retrospective fix: `AttributeTemplateRepository.findAllByScope()` thêm mới; `AttributeTemplatePersistenceAdapter` implement; import `AttributeScope` trong adapter

### 2026-06-15 — Infrastructure layer
- `CategoryJpaEntity` — int `level` field (không phải enum, map trong mapper)
- `CategoryClosureKey`, `CategoryClosureJpaEntity` — composite PK
- `CategoryAssignmentKey`, `CategoryAttributeAssignmentJpaEntity` — composite PK; managed separately (không cascade từ CategoryJpaEntity vì composite PK gây phức tạp với orphanRemoval)
- `CategoryJpaRepository`, `CategoryClosureJpaRepository`, `CategoryAttributeAssignmentJpaRepository`
- Closure: 2 native INSERT — `insertSelfReference` + `insertAncestorReferences` (thay cho UNION ALL)
- `CategoryMapper` — `int → CategoryLevel` conversion; batch assignment load để tránh N+1 trong `findAll()`
- `CategoryPersistenceAdapter` — assignment sync dùng delete+reinsert pattern
- `CategoryRepository.findRoots()` đổi thành `findAll()` — tree query cần toàn bộ flat list

### 2026-06-15 — Domain layer
- `CategoryId`, `CategoryLevel` (với `nextLevel()`), `CategoryStatus`, `CategoryErrorCode` (codes 20201–20207)
- `CategoryAttributeAssignment` VO — equals/hashCode trên `attributeTemplateId`
- `CategoryUpdatedEvent` — eventId dùng `UUID.randomUUID()` (không inject ULIDGenerator vào domain)
- `Category` AR — `createRoot`, `createChild`, `update`, `assignAttribute`, `updateAssignment`, `removeAssignment`
- `CategoryRepository` interface — 6 methods
