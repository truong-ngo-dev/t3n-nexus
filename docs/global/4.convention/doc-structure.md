# Convention: Documentation Structure

> **Dành cho AI agents**: Đây là quy ước tổ chức tài liệu cho toàn bộ project.
> Trước khi tạo bất kỳ file doc nào, xác định đúng tier và đặt đúng chỗ.

---

## 3-Tier Structure

```
docs/
├── global/      # Kiến thức nền — đọc một lần, áp dụng mãi
├── service/     # Thiết kế cụ thể của từng component
└── feature/     # Flow xuyên service của từng tính năng
```

---

## Tier 1 — `global/`

**Scope:** Toàn hệ thống. Không phụ thuộc vào service hay feature cụ thể.

**Nguyên tắc:** Nếu một thông tin cần biết *trước khi* bắt đầu bất kỳ service nào → thuộc `global/`.

```
global/
├── 1.requirement/     # Yêu cầu nghiệp vụ, NFR, actors
├── 2.architecture/    # C4, bounded contexts, communication, event catalog, ADR
├── 3.technical/       # Cross-cutting: Saga-DLQ, outbox, idempotency patterns
└── 4.convention/      # Coding conventions, doc structure, DDD rules
```

**Vòng đời:** Ít thay đổi. Khi thay đổi, impact rộng → review cẩn thận.

---

## Tier 2 — `service/`

**Scope:** Một component cụ thể (microservice hoặc shared library).

**Nguyên tắc:** Nếu tài liệu chỉ mô tả *một* service/lib → thuộc `service/`. Nếu cần nhắc đến 2+ service → thuộc `feature/`.

```
service/
├── {service-name}/
│   ├── service.md     # Domain model, use case list, integration contract
│   ├── data.md        # DB schema, index strategy (bỏ qua nếu là lib)
│   └── api.yaml       # OpenAPI spec (bỏ qua nếu không expose HTTP)
└── {lib-name}/
    └── service.md     # API surface, cách dùng, auto-config (không có data/api)
```

**Vòng đời:** Tạo khi bắt đầu design service. Cập nhật liên tục khi implement.

---

## Tier 3 — `feature/`

**Scope:** Một tính năng end-to-end, span qua nhiều services.

**Nguyên tắc:** Nếu cần sequence diagram có 2+ service tham gia → thuộc `feature/`. Service doc chỉ mô tả *phần tham gia* của service đó, link về feature doc để đọc flow đầy đủ.

```
feature/
└── {feature-name}/
    ├── design.md          # Business flow, actors, saga steps, business bạnrules
    ├── sequence.puml      # Cross-service sequence diagram
    └── implementation.md  # Task checklist, thứ tự triển khai, verify criteria
```

**Vòng đời:** Tạo khi bắt đầu feature. `design.md` và `sequence.puml` cập nhật nếu flow thay đổi. `implementation.md` tick dần khi implement xong.

---

## Phân loại nhanh

| Câu hỏi                                      | Nếu "có" → tier              |
|----------------------------------------------|------------------------------|
| Cần biết trước khi code bất kỳ service nào?  | `global/`                    |
| Chỉ mô tả 1 service/lib?                     | `service/`                   |
| Mô tả flow có 2+ service tham gia?           | `feature/`                   |
| Là quyết định kiến trúc không thể đảo ngược? | `global/2.architecture/adr/` |

---

## Template: `service/{name}/service.md`

```markdown
# {Service Name}

## Trách nhiệm

_{Một đoạn ngắn: service này làm gì, không làm gì.}_

## Domain Model

### Aggregates

| Aggregate | Root Entity | Invariants |
|---|---|---|
| `{Name}` | `{Entity}` | _{Rule không được vi phạm}_ |

### Commands

| Command | Handler | Publishes |
|---|---|---|
| `{CommandName}` | `{HandlerClass}` | `{EventName}` |

### Domain Events

| Event | Trigger | Consumers (downstream) |
|---|---|---|
| `{EventName}` | _{Khi nào}_ | `{service-name}` |

### Business Rules

- _{Rule 1}_
- _{Rule 2}_

## Use Cases — tham gia

| Feature | Role | Handles | Publishes |
|---|---|---|---|
| [{feature-name}](../../feature/{feature-name}/design.md) | _{coordinator / participant / consumer}_ | _{events/commands}_ | _{events}_ |

## Integration Contract

### Publishes (Kafka)

| Topic | Event | Partition Key |
|---|---|---|
| `{topic.name}` | `{EventName}` | `{aggregateId}` |

### Consumes (Kafka)

| Topic | Event | Handler | Idempotency |
|---|---|---|---|
| `{topic.name}` | `{EventName}` | `{HandlerClass}` | `{field dùng để dedup}` |

### Sync Calls

| Direction | Counterpart | Protocol | Endpoint |
|---|---|---|---|
| Outbound | `{service-name}` | REST / gRPC | `{path hoặc method}` |
| Inbound | `{service-name}` | REST | `{path}` |

## Dependencies

- **Services:** _{Các service cần chạy cùng}_
- **Infrastructure:** _{DB engine, cache, broker topic cần tồn tại}_
```

---

## Template: `service/{name}/data.md`

```markdown
# Data Schema — {Service Name}

## Database

**Engine:** _{PostgreSQL / MongoDB / Redis — ghi rõ lý do nếu không phải mặc định}_

## Tables / Collections

### `{table_name}`

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `uuid` | NO | PK |
| `{field}` | `{type}` | _{YES/NO}_ | _{ghi chú nếu cần}_ |
| `created_at` | `timestamp` | NO | |

**Indexes:**
- `{index_name}` on `({columns})` — _{lý do}_

### `outbox_events` _(nếu service dùng Outbox pattern)_

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | PK |
| `aggregate_type` | `varchar` | |
| `aggregate_id` | `uuid` | |
| `event_type` | `varchar` | |
| `payload` | `jsonb` | |
| `created_at` | `timestamp` | |
| `published_at` | `timestamp` | NULL = chưa publish |
```

---

## Template: `feature/{name}/design.md`

```markdown
# Feature: {Feature Name}

## Mục tiêu

_{1-2 câu: tính năng này giải quyết vấn đề gì cho ai.}_

## Actors

| Actor | Role |
|---|---|
| `{Actor}` | _{Làm gì trong flow này}_ |

## Services tham gia

| Service | Role |
|---|---|
| `{service-name}` | _{coordinator / participant / consumer}_ |

## Happy Path

```
1. {Actor} → {action}
2. {service-name} → {xử lý gì} → publish {EventName}
3. {service-name} → nhận {EventName} → {xử lý gì} → publish {EventName}
...
```

## Failure Scenarios

| Điểm thất bại | Compensating action | Kết quả cuối |
|---|---|---|
| _{service fail ở bước N}_ | _{ai compensate}_ | _{state cuối cùng}_ |

## Business Rules

- _{Rule cần enforce trong flow này}_

## Sequence Diagram

Xem [`sequence.puml`](sequence.puml).
```

---

## Template: `feature/{name}/implementation.md`

```markdown
# Implementation Plan: {Feature Name}

**Design**: [`design.md`](design.md) | **Sequence**: [`sequence.puml`](sequence.puml)

---

## Docs cần tạo / cập nhật

| Tài liệu | Hành động | Nội dung |
|---|---|---|
| `service/{name}/service.md` | Tạo mới / Cập nhật | _{commands, events mới}_ |
| `service/{name}/data.md` | Tạo mới / Cập nhật | _{tables mới}_ |
| `service/{name}/api.yaml` | Tạo mới / Cập nhật | _{endpoints mới}_ |
| `global/2.architecture/event-catalog.md` | Cập nhật | _{events của feature này}_ |

---

## Thứ tự triển khai

_Implement theo dependency chain — producer trước consumer._

### Bước 1 — {Tên bước}

- [ ] _{Task}_

### Bước N — Integration & docs

- [ ] Cập nhật `global/2.architecture/event-catalog.md`
- [ ] Tạo ADR nếu có quyết định kỹ thuật mới
- [ ] Tick checklist bên dưới

---

## Checklist hoàn thành

- [ ] Happy path chạy end-to-end
- [ ] Failure scenarios đã test
- [ ] Outbox hoạt động — không mất event khi restart
- [ ] Idempotency — duplicate event không tạo duplicate state
- [ ] Tất cả service docs cập nhật đúng thực tế
- [ ] Event catalog cập nhật payload cuối cùng
```
