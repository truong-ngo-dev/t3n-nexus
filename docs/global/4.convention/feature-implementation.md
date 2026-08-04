# Convention: Feature Implementation

Quy ước áp dụng cho mọi feature trong `docs/feature/`.

Cấu trúc file (`design.md` + `implementation.md`) và template chi tiết: xem `doc-structure.md` §Tier 3.
File này chỉ tập trung vào 1 việc: **cách tách `implementation.md` thành phase**, và **cách ghi nhật ký session mà không phình to theo thời gian**.

---

## `implementation.md` là 1 file duy nhất, không tách `progress/`

Từng có giai đoạn dùng `progress/phase-N-*.md` riêng cho mỗi phase — bỏ. Lý do: checkbox đã tick là 1 sự kiện lịch sử, không bao giờ "sai" theo thời gian nên rẻ để giữ mãi trong `implementation.md`; nhưng mô tả tiến độ dạng prose ("làm được gì, còn gì") thì có rủi ro đọc nhầm thành trạng thái hiện tại nếu để rải rác nhiều file theo thời gian. Gộp về 1 file + 1 mục Session Log nén giải quyết cả hai: checklist giữ chi tiết vô hạn (rẻ), prose bị ép phải cô đọng (đắt nếu lan man).

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

## Template 1 phase (trong `implementation.md`, không phải file riêng)

```markdown
## Phase N — <tên>

**Status:** `TODO` | `IN_PROGRESS` | `DONE` | `BLOCKED`

- [ ] task 1
- [ ] task 2

**Verify:** <assertion cụ thể để xác nhận phase hoàn thành>
```

Nhiều phase = nhiều section `## Phase N` liên tiếp trong cùng `implementation.md`. Không tạo file mới cho phase mới.

---

## Session Log — quy tắc ghi

Đặt cuối `implementation.md`. Chỉ ghi dòng mới khi có **quyết định bất ngờ hoặc blocker phát sinh** — không ai đoán trước được từ `design.md`. Không ghi khi session chỉ đơn thuần hoàn thành đúng như plan (lúc đó tick checkbox là đủ).

```markdown
## Session Log

- 2026-06-20: Phase 3 (Category) — closure table chậm hơn dự tính, thêm index composite (categoryId, level).
- 2026-06-25: Phase 5 — đổi hướng: image upload dùng presigned URL thay vì multipart, vì blocked bởi NFR upload 5MB.
```

Cùng nguyên tắc filter đã áp dụng cho memory của AI agent (xem `agent-workflow.md`) — chỉ lưu cái không ai đoán trước được, không lưu cái tự suy ra được từ code/design.

---

## Những gì KHÔNG lưu vào Session Log / memory

Routine progress ("done phase X đúng kế hoạch") không có giá trị tra cứu về sau — nó tự nhiên đã được thể hiện qua checkbox đã tick và qua chính code đã merge. Chỉ lưu **quyết định bất ngờ** hoặc **constraint phát sinh** không ai đoán trước.
