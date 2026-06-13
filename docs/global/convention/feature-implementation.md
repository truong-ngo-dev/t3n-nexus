# Convention: Feature Implementation

Quy ước áp dụng cho mọi feature trong `docs/design/features/`.

---

## Cấu trúc thư mục

```
docs/design/features/<feature-name>/
├── design.md            # domain analysis, flow, business rules
├── sequence.puml        # sequence diagram
├── implementation.md    # master plan — checklist tổng, danh sách docs cần tạo
└── progress/
    ├── phase-1-<name>.md
    ├── phase-2-<name>.md
    └── ...
```

`implementation.md` là **master plan** — không thay đổi thường xuyên.  
`progress/phase-N-*.md` là **living document** — update sau mỗi session.

---

## Tiêu chí phân tách phase

Áp dụng theo thứ tự ưu tiên:

### 1. Dependency direction — producer trước consumer

Phase N không bắt đầu cho đến khi phase N-1 publish được contract (API schema hoặc event schema) có thể verify. Tiêu chí cứng với event-driven system.

### 2. Independently verifiable — mỗi phase kết thúc bằng 1 assertion cụ thể

Không kết thúc phase bằng "code xong" hay "deploy xong". Mỗi phase phải có verify step rõ ràng:

- Câu query SQL trả về row mong đợi
- HTTP request trả về status + payload mong đợi
- Message xuất hiện trong topic / outbox table

### 3. Blast radius nhỏ — 1 phase = 1 service hoặc 1 shared layer

Không gộp 2 service vào 1 phase. Khi bị blocked cần khoanh vùng nhanh. Shared libs tách riêng vì thay đổi ở đó affect tất cả service.

### 4. Session-size fit — hoàn thành trong 1–3 session

- Estimate > 3 session → split nhỏ hơn
- Estimate < 1 session → gộp với phase liền kề

---

## Template phase file

```markdown
# Phase N — <tên>

**Status:** `TODO` | `IN_PROGRESS` | `DONE` | `BLOCKED`
**Started:** YYYY-MM-DD
**Completed:** —

## Checklist
- [ ] task 1
- [ ] task 2

## Verify
<!-- Assertion cụ thể để xác nhận phase hoàn thành -->

## Session Log

### YYYY-MM-DD
- Làm được: ...
- Còn lại: ...
- Blocker (nếu có): ...
```

---

## Những gì KHÔNG lưu vào memory

Log session và trạng thái phase lưu tại `progress/`. Memory chỉ lưu **quyết định bất ngờ** hoặc **constraint phát sinh** không ai đoán trước.
