# T3Nexus — Documentation Guide

## Cấu trúc thư mục

```
docs/
├── requirement.md              # NFR, Actors, BC summary, Technical Scenarios
├── domain/                     # DDD analysis — bounded contexts (detail), ubiquitous language
├── architecture/               # System-level design
│   ├── overview.md             # C4 topology, communication patterns
│   ├── tech-stack.md           # Toàn bộ tech stack với justification
│   ├── service-mapping.md      # 23 services, phân nhóm theo domain
│   ├── communication.md        # Sync (REST/gRPC) + Async (Kafka) + DB isolation
│   ├── deployment.md           # Local dev + Demo AWS + IaC
│   └── adr/                    # Architecture Decision Records (immutable)
├── design/                     # Component-level design
│   ├── features/               # Cross-service UC flows — mỗi UC là 1 folder
│   │   └── {uc-name}/          #   design.md + implementation.md + sequence.puml
│   ├── libs/                   # Shared library design
│   ├── services/               # Per-service design (tích lũy theo UC)
│   ├── api/                    # OpenAPI specs (tích lũy theo UC)
│   ├── data/                   # DB schema (tích lũy theo UC)
│   └── events/                 # Event catalog
├── convention/                 # Coding conventions, javadoc
├── implementation/             # Getting started, coding guides
└── diagrams/                   # PlantUML / Mermaid diagrams
```

---

## Tài liệu base — tạo trước khi bắt đầu dev bất kỳ service nào

Những tài liệu này là **inputs** cho toàn bộ quá trình phát triển. Phải có trước.

| File                              | Nội dung                                             | Phase        |
|-----------------------------------|------------------------------------------------------|--------------|
| `requirement.md`                  | NFR, Actors, BC summary, Technical Scenarios         | Analysis     |
| `domain/bounded-contexts.md`      | BC detail — business rules, patterns, relationships  | Analysis     |
| `domain/ubiquitous-language.md`   | Glossary — định nghĩa thuật ngữ theo từng BC         | Analysis     |
| `design/events/event-catalog.md`  | Domain events — topic, payload, consumers            | Analysis     |
| `architecture/overview.md`        | C4 Level 1+2 — service topology, communication style | Architecture |
| `architecture/tech-stack.md`      | Toàn bộ tech stack với justification                 | Architecture |
| `architecture/service-mapping.md` | 23 services, phân nhóm theo domain                   | Architecture |
| `architecture/communication.md`   | Sync/async protocol, DB isolation                    | Architecture |
| `architecture/deployment.md`      | Local dev + Demo AWS + IaC                           | Architecture |
| `architecture/adr/`               | Architecture Decision Records                        | Architecture |
| `design/libs/overview.md`         | Danh sách shared libs, scope và trách nhiệm từng lib | Architecture |

---

## Flow phát triển theo phase

### Phase 1 — Problem Space (Analysis)

Mục tiêu: hiểu **cần làm gì**, chưa quan tâm làm thế nào.

**Tạo mới:**
- `requirement.md`
- `domain/bounded-contexts.md`
- `domain/ubiquitous-language.md`
- `diagrams/use-case-overview.puml`
- `design/events/event-catalog.md` *(draft — domain events từ Event Storming)*

---

### Phase 2 — Architecture (High-Level Design)

Mục tiêu: quyết định hệ thống trông như thế nào — service nào, giao tiếp ra sao, pattern nào.

**Tạo mới:**
- `architecture/overview.md` — C4 L1+L2, service topology
- `architecture/adr/001-*.md` — ADR cho các quyết định kiến trúc lớn
- `design/libs/overview.md` — danh sách libs, scope từng lib
- `diagrams/c4-containers.puml`

**Cập nhật:**
- `design/events/event-catalog.md` — bổ sung integration events (Kafka)

---

### Phase 3 — Detailed Design (per service / per lib)

Mục tiêu: trước khi code, biết chính xác component đó làm gì.

**Tạo mới (per service):**
- `design/services/{name}.md` — Aggregate, Command, Query, Domain Event, Dependencies
- `design/api/{name}.yaml` — OpenAPI spec, contract trước khi code
- `design/data/{name}.md` — DB schema, index strategy

**Tạo mới (per lib):**
- `design/libs/{name}.md` — interface, auto-config, cách dùng

**Cập nhật:**
- `domain/ubiquitous-language.md` — tinh chỉnh khi design làm rõ thêm ngôn ngữ
- `architecture/adr/` — quyết định mới nảy sinh khi design chi tiết

---

### Phase 4 — Implementation

Mục tiêu: code, và cập nhật tài liệu khi thực tế khác với thiết kế.

**Cập nhật liên tục:**
- `design/services/{name}.md` — khi schema, event, dependency thay đổi
- `design/data/{name}.md` — khi schema thực tế sau migration khác design
- `design/events/event-catalog.md` — event payload cuối cùng sau implement

**Tạo thêm khi cần:**
- `architecture/adr/` — mỗi khi có quyết định kỹ thuật quan trọng (pivot, trade-off)
- `diagrams/sequence-{flow}.puml` — khi flow phức tạp (Saga, login, checkout...)
- `implementation/conventions.md` — coding conventions, package structure

---

## Flow phát triển theo UC (Use Case driven)

Project phát triển theo từng UC. Mỗi UC có thể chạm nhiều services.

### Flow cho mọi UC

```
1. Đọc requirement.md → xác định UC, identify services liên quan
         ↓
2. Tạo design/features/{uc-name}.md        ← end-to-end flow
   Tạo diagrams/sequence-{uc-name}.puml    ← visualize cross-service
         ↓
3. Với mỗi service bị ảnh hưởng:
   → Tạo mới hoặc cập nhật design/services/{name}.md  (chỉ phần UC này)
   → Cập nhật design/api/{name}.yaml nếu expose endpoint mới
   → Cập nhật design/data/{name}.md nếu có schema mới
         ↓
4. Cập nhật design/events/event-catalog.md ← events UC này sinh ra
         ↓
5. Code từng service
         ↓
6. Cập nhật feature doc + service docs nếu flow thay đổi
   Tạo ADR nếu có quyết định kỹ thuật mới
```

Chi tiết và ví dụ đầy đủ: `design/features/place-order.md`

---

## Quy tắc ADR

ADR (Architecture Decision Record) là loại tài liệu **không bao giờ sửa** — chỉ tạo mới.

- Mỗi quyết định kiến trúc quan trọng = 1 file mới
- Format tên: `{number}-{slug}.md` — VD: `003-session-store-redis.md`
- Nội dung tối thiểu: quyết định gì, tại sao, trade-off là gì
- Tạo nhiều nhất trong Phase 4 khi thực tế code buộc phải chọn hướng

---

## Vòng đời tài liệu

| Tài liệu | Tạo | Sửa | Ghi chú |
|---|---|---|---|
| `requirement.md` | Phase 1 | Hiếm | Source of truth nghiệp vụ |
| `bounded-contexts.md` | Phase 1 | Khi BC tách/gộp | |
| `ubiquitous-language.md` | Phase 1 | Xuyên suốt | Living document |
| `architecture/overview.md` | Phase 2 | Khi topology thay đổi | |
| `adr/*.md` | Khi có quyết định | **Không bao giờ sửa** | Immutable log |
| `design/libs/overview.md` | Phase 2 | Khi thêm/bỏ lib | |
| `design/services/{name}.md` | Phase 3 | Phase 4 liên tục | Living document |
| `design/api/{name}.yaml` | Phase 3 | Khi API thay đổi | |
| `design/data/{name}.md` | Phase 3 | Khi schema thay đổi | |
| `design/events/event-catalog.md` | Phase 1 | Hoàn thiện dần theo UC | |
| `design/features/{uc}/design.md` | Khi bắt đầu UC phức tạp | Khi flow thay đổi | Living document |
| `design/features/{uc}/implementation.md` | Khi bắt đầu UC phức tạp | Khi task hoàn thành | Checklist |
| `design/features/{uc}/sequence.puml` | Khi bắt đầu UC phức tạp | Khi flow thay đổi | |
