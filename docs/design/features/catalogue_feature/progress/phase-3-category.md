# Phase 3 — Category

**Status:** TODO  
**Started:** —  
**Completed:** —

## Checklist

### Domain
- [ ] `CategoryId` — typed UUID wrapper
- [ ] `CategoryLevel` enum: `L1(1) | L2(2) | L3(3)`
- [ ] `CategoryStatus` enum: `ACTIVE | INACTIVE`
- [ ] `CategoryAttributeAssignment` value object — record `{ attributeTemplateId, isVariantDefining, isRequired, isFilterable, displayOrder }`. Equals/hashCode trên `attributeTemplateId` duy nhất
- [ ] `CategoryErrorCode` enum — `CATEGORY_NOT_FOUND(404)`, `CATEGORY_SLUG_EXISTS(409)`, `MAX_DEPTH_EXCEEDED(422)`, `HAS_CHILDREN(409)`, `HAS_PRODUCT_REFERENCE(409)`, `ASSIGNMENT_ALREADY_EXISTS(409)`, `ASSIGNMENT_NOT_FOUND(404)`
- [ ] `CategoryUpdatedEvent` domain event — `{ categoryId }`
- [ ] `Category` aggregate root — `{ categoryId, name, slug, parentId, level, imageUrl, status, assignments: List<CategoryAttributeAssignment> }`
- [ ] `Category.createRoot(id, name, slug)` static factory — level=L1, parentId=null
- [ ] `Category.createChild(id, name, slug, parentId, parentLevel)` static factory — **guard**: `parentLevel == L3` → throw `MAX_DEPTH_EXCEEDED`
- [ ] `Category.update(name, imageUrl)` → raise `CategoryUpdatedEvent`
- [ ] `Category.assignAttribute(assignment)` — **guard**: duplicate `attributeTemplateId` → throw `ASSIGNMENT_ALREADY_EXISTS`
- [ ] `Category.updateAssignment(assignment)` — tìm theo `attributeTemplateId`, replace toàn bộ config
- [ ] `Category.removeAssignment(templateId)` — throw `ASSIGNMENT_NOT_FOUND` nếu không tồn tại
- [ ] `CategoryRepository` interface — `findById`, `existsBySlug`, `existsByParentId(categoryId)`, `hasProductReference(categoryId)`, `save`, `findRoots()`

### Infrastructure — Persistence
- [ ] `CategoryJpaEntity` — không có `@OneToMany children` ở đây; tree navigate qua closure table
- [ ] `CategoryClosureJpaEntity` — `{ ancestorId, descendantId, depth }`, composite PK
- [ ] `CategoryAttributeAssignmentJpaEntity`
- [ ] `CategoryJpaRepository`
- [ ] `CategoryClosureJpaRepository` — queries:
  - `findByAncestorId(ancestorId)` — lấy tất cả descendants
  - `findByDescendantId(descendantId)` — lấy path to root
  - `countByAncestorIdAndDepth1(ancestorId)` — check có children không
- [ ] `CategoryMapper`
- [ ] `CategoryPersistenceAdapter implements CategoryRepository`  
      **⚠️ Closure insert logic khi tạo Category**:
      ```
      INSERT INTO category_closure(ancestor_id, descendant_id, depth)
        -- self-referencing row
        VALUES (newId, newId, 0)
        -- copy parent's ancestors + 1
        UNION ALL
        SELECT ancestor_id, newId, depth + 1
        FROM category_closure WHERE descendant_id = parentId
      ```

### Application — Queries (implement trước Commands để verify tree)
- [ ] `GetCategoryTree` — `Query()`, `Result(List<CategoryTreeNode>)`, `Handler`  
      `CategoryTreeNode{ id, name, slug, level, imageUrl, children: List<CategoryTreeNode> }`  
      Load flat list → build tree in memory (max depth 3, manageable)
- [ ] `GetCategoryAttributes` — `Query(UUID categoryId)`, `Result(List<CategoryAttributeDto>)`, `Handler`  
      Merge: (1) tất cả GLOBAL AttributeTemplates + (2) CATEGORY templates đã assign vào category này  
      Sort theo `displayOrder`

### Application — Commands
- [ ] `CreateCategory` — `Command(name, slug, parentId?)`, `Result(UUID id)`, `Handler`  
      check slug unique → load parent nếu có → `Category.createRoot()` hoặc `Category.createChild()` → save (với closure insert)
- [ ] `UpdateCategory` — `Command(UUID id, name, imageUrl)`, `Result`, `Handler`
- [ ] `DeleteCategory` — `Command(UUID id)`, `Result`, `Handler`  
      **Guard**: `existsByParentId(id)` → throw `HAS_CHILDREN`; `hasProductReference(id)` → throw `HAS_PRODUCT_REFERENCE`
- [ ] `AssignAttributeToCategory` — `Command(UUID categoryId, UUID templateId, isVariantDefining, isRequired, isFilterable, displayOrder)`, `Result`, `Handler`
- [ ] `UpdateCategoryAttributeAssignment` — `Command(UUID categoryId, UUID templateId, ...)`, `Result`, `Handler`
- [ ] `RemoveCategoryAttributeAssignment` — `Command(UUID categoryId, UUID templateId)`, `Result`, `Handler`

### Presentation
- [ ] `presentation/category/model/`: `CreateCategoryRequest`, `UpdateCategoryRequest`, `AssignAttributeRequest`, `UpdateAssignmentRequest`, `CategoryTreeResponse`, `CategoryAttributeResponse`
- [ ] `CategoryController`:
  - `GET /api/categories` → `GetCategoryTree`
  - `POST /api/admin/categories` → `CreateCategory`
  - `PUT /api/admin/categories/{id}` → `UpdateCategory`
  - `DELETE /api/admin/categories/{id}` → `DeleteCategory`
  - `GET /api/categories/{id}/attributes` → `GetCategoryAttributes`
  - `POST /api/admin/categories/{id}/attributes` → `AssignAttributeToCategory`
  - `PUT /api/admin/categories/{id}/attributes/{templateId}` → `UpdateCategoryAttributeAssignment`
  - `DELETE /api/admin/categories/{id}/attributes/{templateId}` → `RemoveCategoryAttributeAssignment`

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
