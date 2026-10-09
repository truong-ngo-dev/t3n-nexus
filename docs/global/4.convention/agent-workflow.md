# Convention: Agent Workflow

> **Dành cho AI agents**: Đọc file này **trước tiên** trong mọi session.
> Sau khi đọc xong, **ngay lập tức** xác định scenario bên dưới và thực hiện reading list tương ứng — không chờ thêm chỉ thị.
> Mọi đường dẫn trong file này đều tính từ gốc `docs/`.

---

## Xác định scenario

```
Bắt đầu session → Tôi đang làm gì?
│
├─ Feature mới, chưa có doc  ──────────────→ Scenario A
├─ Feature đang dở, có implementation.md  → Scenario B
└─ Fix bug / task nhỏ trong service       → Scenario C
```

---

## Scenario A — Bắt đầu feature mới

### Bắt buộc đọc (theo thứ tự)

| # | Tài liệu                                       | Mục đích                                      |
|---|------------------------------------------------|-----------------------------------------------|
| 1 | `global/1.requirement/requirement.md`          | Actors, business rules, NFR của feature       |
| 2 | `global/2.architecture/1. bounded-contexts.md` | Domain language, BC liên quan                 |
| 3 | `global/2.architecture/5. event-catalog.md`    | Events đã có — không tạo duplicate            |
| 4 | `global/2.architecture/4. communication.md`    | Quy tắc async/sync                            |
| 5 | `global/4.convention/ddd-structure.md`         | Cấu trúc code bắt buộc                        |
| 6 | `service/{each-service}/analysis.md` *(nếu đã có)* | Nghiệp vụ đã chốt **kèm lý do** — nguồn của mọi quy tắc (INV), hành động (CMD), sự kiện (EVT), truy vấn (RM) |
| 7 | `service/{each-service}/service.md`            | Cách hiện thực các phần tử trên (bám ID của analysis) — cơ chế, concurrency, tích hợp |

> Service chưa có `analysis.md` thì `service.md` vẫn là nguồn duy nhất — chưa tách tầng. Mẫu và quan hệ giữa các
> file: `doc-structure.md` §Tier 2.

### Đọc thêm nếu liên quan

| Tài liệu                                     | Khi nào                               |
|----------------------------------------------|---------------------------------------|
| `global/2.architecture/adr/`                       | Feature dính đến quyết định đã có ADR |
| `global/3.technical/saga-dlq-integration.md`       | Feature có Saga / compensation        |
| `global/3.technical/dlq-implementation-notes.md`   | Feature có Kafka consumer — cần biết DLQ config/retry |

### Confirm với user trước khi code

Sau khi đọc xong reading list, báo cáo ngắn gọn:

1. **Feature này chạm những service nào** — liệt kê tên
2. **Saga hay không** — nếu có, liệt kê các bước compensation
3. **Events mới cần tạo** — tên event, producer, consumers
4. **Điểm chưa rõ** — bất kỳ ambiguity nào trong requirement hoặc existing design

Kèm theo đề xuất những tài liệu có thể cần cập nhật nếu design thay đổi trong quá trình implement:

- Events mới / payload thay đổi → `global/2.architecture/5. event-catalog.md`
- API mới → `service/{name}/api.yaml`
- Schema mới → `service/{name}/data.md`
- Flow thực tế khác design → `feature/{name}/design.md` (bao gồm sequence diagram nhúng trong file)
- Quyết định kiến trúc mới → `global/2.architecture/adr/{n}-{slug}.md`. Không chắc là ADR hay chỉ là cập nhật bảng tra cứu / cookbook? Xem `doc-structure.md` §Phân biệt architecture/technical/adr

Chờ user confirm trước khi tạo file và bắt đầu code.

### Tạo trước khi code

```
feature/{feature-name}/
├── design.md          ← flow, actors, business rules, failure scenarios, sequence diagram nhúng
└── implementation.md  ← task checklist, thứ tự triển khai, Session Log
```

Template: xem `doc-structure.md`.

---

## Scenario B — Tiếp tục feature đang dở

### Bắt buộc đọc (theo thứ tự)

| # | Tài liệu                               | Mục đích                                             |
|---|----------------------------------------|------------------------------------------------------|
| 1 | `feature/{name}/implementation.md`     | Tiến độ hiện tại — task nào done, task nào tiếp theo |
| 2 | `feature/{name}/design.md`             | Re-orient: flow và business rules                    |
| 3 | `service/{name}/analysis.md` *(nếu đã có)* | Nghiệp vụ và lý do của service sẽ làm session này |
| 4 | `service/{name}/service.md`            | Thiết kế kỹ thuật của service sẽ làm session này     |
| 5 | `global/4.convention/ddd-structure.md` | Cấu trúc code                                        |

### Không cần đọc lại

`requirement.md`, `bounded-contexts.md`, `communication.md` — chỉ đọc lại khi có thay đổi ở đó.

---

## Scenario C — Fix bug / task nhỏ

### Bắt buộc đọc

| # | Tài liệu                               | Mục đích                                 |
|---|----------------------------------------|------------------------------------------|
| 1 | `service/{name}/analysis.md` *(nếu đã có)* | Quy tắc nghiệp vụ và lý do — bug thường là code lệch quy tắc ở đây |
| 2 | `service/{name}/service.md`            | Thiết kế kỹ thuật, business rules của service |
| 3 | `global/4.convention/ddd-structure.md` | Cấu trúc code                            |

Với task nhỏ chỉ đọc phần liên quan (tra theo ID `INV-`/`CMD-`/`RM-` trong code hoặc lỗi) — không cần đọc cả file.

---

## Trigger cập nhật tài liệu

Mỗi khi code thay đổi một trong những điều sau, **cập nhật doc ngay** — không để cuối session.

**Thứ tự khi thay đổi lan qua nhiều tầng:** `analysis.md` → `service.md` → (`data.md`, `api.yaml`, code). Yêu cầu
nghiệp vụ đổi thì sửa `analysis.md` **trước** (quy tắc + lý do + phương án bị loại), rồi mới tới `service.md`; lý do
nghiệp vụ không viết vào `service.md`. `data.md`/`api.yaml` là hệ quả của `service.md` — không suy thiết kế từ chúng.

| Thay đổi trong code                           | Tài liệu cần cập nhật                                                                  |
|-----------------------------------------------|----------------------------------------------------------------------------------------|
| Quy tắc / hành vi / trạng thái nghiệp vụ đổi (INV, CMD, ma trận trạng thái, RM) | `service/{name}/analysis.md` trước, rồi `service.md` §3–§5 cho phần hiện thực |
| Cơ chế kỹ thuật đổi (khoá, cache, idempotency, job, retry)  | `service/{name}/service.md` (§9–§12, §14)                                       |
| Thêm domain event mới                         | `global/2.architecture/5. event-catalog.md` + `service/{name}/service.md` §7.1 Publishes (và `analysis.md` §8.1 nếu là sự kiện nghiệp vụ mới) |
| Sửa payload của event                         | `global/2.architecture/5. event-catalog.md`                                            |
| Thêm / sửa API endpoint                       | `service/{name}/api.yaml`                                                              |
| Thêm / sửa table hoặc column                  | `service/{name}/data.md`                                                               |
| Thêm dependency (gọi service khác, topic mới) | `service/{name}/service.md` §7 Integration contract + §17 Dependencies                  |
| Flow thực tế khác với design                  | `feature/{name}/design.md` (kể cả sequence diagram nhúng trong file)                   |
| Phát sinh quyết định kiến trúc mới            | Tạo `global/2.architecture/adr/{n}-{slug}.md` — xem ADR rules                          |
| Hoàn thành một task                           | Tick checkbox trong `feature/{name}/implementation.md`                                 |
| Session có blocker/deviation bất ngờ so với plan | Thêm 1 dòng vào Session Log của `feature/{name}/implementation.md` — không ghi routine progress |

---

## Quy tắc ADR

Tạo ADR khi quyết định **không thể đảo ngược** hoặc **ảnh hưởng đến nhiều service**:

- Chọn DB engine cho service mới
- Thêm sync call pair mới (ngoài 4 cặp đã phê duyệt)
- Thay đổi messaging protocol
- Chọn pattern mới (saga, cqrs, event sourcing...)

**Không** tạo ADR cho implementation detail (tên class, cấu trúc package, index strategy).

Format file: `{số tiếp theo}-{slug}.md`. Cập nhật `adr/000-README.md` sau khi tạo.

Ranh giới chi tiết giữa ADR / `architecture/` (bảng tra cứu) / `technical/` (cookbook): xem `doc-structure.md` §Phân biệt architecture/technical/adr.

---

## Checklist kết thúc session

Trước khi kết thúc, verify:

- [ ] Mọi domain event mới đã có trong `event-catalog.md`
- [ ] `service/{name}/service.md` phản ánh đúng state hiện tại
- [ ] Nếu nghiệp vụ đổi: `analysis.md` đã sửa **trước** `service.md`; mọi INV/CMD/EVT/RM mới hoặc đổi có dòng ở `service.md`
- [ ] Mục "Lệch giữa thiết kế và code" (§16 `service.md`) chỉ điền từ việc rà code thật — chưa rà thì để "Chưa rà code"
- [ ] `feature/{name}/implementation.md` đã tick các task đã done
- [ ] Không có TODO nào còn nằm trong code (chuyển thành task trong implementation.md)
- [ ] Nếu flow thay đổi: `design.md` (kể cả sequence diagram nhúng) đã cập nhật
- [ ] Nếu có blocker/deviation bất ngờ trong session: đã ghi vào Session Log của `implementation.md`
