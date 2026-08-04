# Implementation Plan — Tái cấu trúc `docs/`

> Master plan cho việc tái cấu trúc toàn bộ `docs/`. Không thay đổi thường xuyên — mỗi phase tick dần khi triển khai.
> Quyết định nền tảng (đã chốt, không thảo luận lại): xem lịch sử hội thoại phiên tái cấu trúc — tóm tắt lại trong Phase 0.
> File này có thể xoá sau khi Phase cuối hoàn thành và 3 file convention đã phản ánh đúng state cuối.

---

## Quyết định đã chốt (tóm tắt — chi tiết rationale nằm trong Phase 0 khi viết lại convention)

| # | Quyết định                                                                                                                                                                                                                               |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `architecture/` chỉ chứa living-reference (bounded-context, service-map, communication, event-catalog, security-architecture, deployment, tech-stack) — không chứa quyết định 1 lần (→ ADR) hay implementation cookbook (→ `technical/`) |
| 2 | `technical/` chỉ chứa pattern/cookbook cách-làm cho cross-cutting concern — không chứa quyết định, không chứa bảng tra cứu tĩnh                                                                                                          |
| 3 | `tech-stack.md` chuyển từ `technical/` sang `architecture/` — nó là bảng tra cứu, không phải cách-làm                                                                                                                                    |
| 4 | `security-architecture.md` giữ nguyên 1 file trong `architecture/` — đã tự tuân thủ đúng ranh giới, không cần tách                                                                                                                       |
| 5 | `feature/{name}/design.md` nhúng sequence diagram trực tiếp (fenced `plantuml` block) — không còn `sequence.puml` rời                                                                                                                    |
| 6 | `feature/{name}/` bỏ thư mục `progress/phase-N-*.md` — thay bằng 1 mục "Session Log" nén trong `implementation.md`, chỉ ghi khi có blocker/deviation thật, checklist tick trực tiếp trong `implementation.md`                            |
| 7 | Mỗi file phụ của 1 service (`cache.md`, `flashsale.md`...) bắt buộc được `service.md` trỏ tới qua mục "Tài liệu liên quan"                                                                                                               |
| 8 | `docs/design/` (cấu trúc cũ) migrate hết sang `global/ + service/ + feature/` rồi xoá                                                                                                                                                    |

---

## Phase 0 — Chốt convention (làm trước — mọi phase sau dựa vào đây)

**Status:** `DONE`

### Checklist
- [x] `global/4.convention/doc-structure.md` — viết lại theo 8 quyết định trên: tier definition, mục "Phân biệt architecture/technical/adr", template `service.md`/`data.md`/`feature/design.md`/`feature/implementation.md` mới (Tài liệu liên quan, sequence nhúng, Session Log)
- [x] `global/4.convention/agent-workflow.md` — sửa reading-list/trigger-table cho khớp path mới, bỏ mọi ref `sequence.puml`/`progress/`, trỏ `dlq-strategy.md` sang `dlq-implementation-notes.md` (target Phase 1), thêm pointer sang boundary table
- [x] `global/4.convention/feature-implementation.md` — viết lại: bỏ hẳn `progress/phase-N-*.md`, thay bằng phase-section + Session Log trong `implementation.md`, giữ nguyên tiêu chí phân tách phase

### Verify
3 file convention nhất quán với nhau, không còn mô tả `progress/` hay path cũ. ✓ Đã rà lại — không còn ref `sequence.puml` hay `progress/` trong cả 3 file.

**Lưu ý:** convention giờ mô tả state đích (VD `tech-stack.md` đã ở `architecture/`, `dlq-strategy.md` đã thành `dlq-implementation-notes.md`) — các phase sau sẽ move file thật cho khớp.

---

## Phase 1 — Tách `global/2.architecture/6. dlq-strategy.md`

**Status:** `DONE`

Ví dụ áp dụng khung ADR/architecture/technical: file này hiện trộn cả 3.

### Checklist
- [x] Tạo `adr/011-dlq-per-service-strategy.md` — quyết định "1 DLQ/service, ngoại lệ email-worker tách theo tier" + rationale rút gọn (từ mục 4 file gốc)
- [x] Thêm mục "DLQ Topics" vào `5. event-catalog.md` (bảng riêng cuối file, không phải cột trong bảng per-producer — DLQ là khái niệm theo consumer, không theo producer/event, gộp cột sẽ lặp lại giá trị nhiều lần)
- [x] Tạo `global/3.technical/dlq-implementation-notes.md` từ mục 6 file gốc (partition key preserved, debug header, infinite-loop, retry config table)
- [x] Cập nhật `adr/000-README.md` với entry 011
- [x] Xoá `6. dlq-strategy.md`

### Verify
`grep -r "dlq-strategy"` trong `docs/` — chỉ còn 2 kết quả hợp lệ (ADR-011 nhắc "đã thay thế file này", chính plan này) — không còn ref nào trỏ tới file mong đợi nó tồn tại. ✓

---

## Phase 2 — Move `tech-stack.md`

**Status:** `DONE`

### Checklist
- [x] `git mv global/3.technical/tech-stack.md global/2.architecture/tech-stack.md`
- [x] Sửa reference trong `0. overview.md`

### Verify
File ở vị trí mới, không còn ref path cũ. ✓

---

## Phase 3 — Dọn `0. overview.md`

**Status:** `DONE`

### Checklist
- [x] Xoá bảng ADR đang lặp lại với `adr/000-README.md` (đang lệch — thiếu ADR-010/011), chỉ giữ pointer
- [x] Bonus fix: phát hiện + sửa lỗi corrupt `" clss/"` prefix trong path của 6 dòng (adr/001,003,004,005,006,007 + deployment.md + tech-stack.md) — không rõ nguồn gốc lỗi, chỉ tồn tại ở file này (đã grep toàn `docs/` xác nhận)

### Verify
Chỉ 1 nơi (`adr/000-README.md`) là nguồn liệt kê ADR. Không còn chuỗi `" clss/"` trong `docs/`. ✓

---

## Phase 4 — Migrate `service.md` cho 3 service chưa có ở tier mới

**Status:** `DONE`

### Checklist
- [x] `git mv design/services/customer-service.md service/customer-service/service.md`
- [x] `git mv design/services/identity-service.md service/identity-service/service.md`
- [x] `git mv design/services/notification-service.md service/notification-service/service.md`
- [x] Grep 3 file tìm ref path cũ — sạch, không cần sửa thêm; deep template rewrite để dành cho lúc thực sự chạm lại service đó (không cần làm ngay)

### Riêng `catalog-service.md` — đã so sánh
- [x] Đọc đầy đủ cả 2 bản — bản cũ (374 dòng) có 3 phần giá trị chưa có ở bản mới: rationale "AttributeTemplate là AR riêng" (Tiki/Shopee/Lazada), flow "Seller tạo Product" (Cartesian matrix), phần còn lại (API tables, Events, Caching, Product Lifecycle 4-state) đều **stale/superseded** — bản mới đã có model 2-trục `status`/`adminBlocked` đúng hơn, `cache.md` đã chi tiết hơn hẳn caching table cũ
- [x] Merge 2 phần giá trị vào `service/catalog-service/service.md` + thêm mục "Tài liệu liên quan" trỏ `cache.md` (hoàn thành luôn 1 phần Phase 10 cho service này)
- [x] Xoá `design/services/catalog-service.md`

### Verify
Mỗi trong 4 service có đúng 1 `service.md`, không còn file trùng ở `design/services/`. ✓

---

## Phase 5 — Migrate `data.md` + `api.yaml`

**Status:** `DONE`

### Checklist
- [x] `git mv design/data/customer-service.md service/customer-service/data.md`
- [x] `git mv design/data/identity-service.md service/identity-service/data.md`
- [x] `git mv design/data/notification-service.md service/notification-service/data.md`
- [x] `git mv design/api/identity-service.yaml service/identity-service/api.yaml`

### Verify
`service/{customer,identity,notification}-service/` mỗi thư mục có `service.md` + `data.md` (identity thêm `api.yaml`). ✓

---

## Phase 6 — Migrate libs

**Status:** `DONE`

### Checklist
- [x] `git mv design/libs/overview.md service/libs/overview.md`

### Verify
`service/libs/overview.md` tồn tại. ✓

---

## Phase 7 — Migrate 6 feature chưa có ở tier mới

**Status:** `DONE`

### Checklist
- [x] `git mv` 5 feature đã track (`personal-info`, `personal-security`, `place-order`, `user-auth`, `customer-registration`) — `payment-checkout` KHÔNG track trong git (phát hiện lúc chạy — dùng `mv` thường thay vì `git mv`)
- [x] Nhúng `sequence.puml` vào `design.md`: `customer-registration` (tách 2 block khớp 2 section CREDENTIAL/OAUTH sẵn có), `personal-info` (1 block), `place-order` (tách 3 block khớp Happy Path/Inventory failed/Payment failed) — xoá cả 3 file `.puml`
- [x] `deferred.md`: giữ nguyên làm file phụ riêng (đủ dài, nội dung ổn định — đúng tiêu chí file phụ ở `doc-structure.md`) — `user-auth/design.md` đã có "Documentation Index" trỏ tới sẵn; thêm dòng trỏ tương tự vào `customer-registration/design.md`
- [x] `user-auth` — không cần restructure: `login-impl.md`/`logout-impl.md`/`session-management.md`/`deferred.md` đã được `design.md` trỏ tới qua "Documentation Index" từ trước — mô hình hub-and-spoke này tự nhiên đáp ứng đúng rule discoverability, giữ nguyên
- [x] Sửa toàn bộ path cũ phát hiện thêm ngoài dự kiến: `payment-checkout` (5 chỗ: ADR-010 link 3-level→2-level, UC gốc link thiếu `global/`, `docs/design/services/notification-service.md`, 2 chỗ `docs/design/features/payment-checkout/*` trỏ chính nó), `user-auth` (3 chỗ link `spring-security-*-bff.md` 3-level→2-level), `place-order/implementation.md` (ref `sequence.puml` đã xoá)

### Verify
Mỗi feature có `design.md` (+ diagram nhúng nếu có) + `implementation.md`, không còn `.puml` trong `feature/`. Grep `docs/design\|docs/architecture` trong `feature/` chỉ còn match ở `catalogue_feature` (Phase 8 xử lý). ✓

---

## Phase 8 — `catalogue_feature`: nén `progress/` thành Session Log

**Status:** `DONE`

### Checklist
- [x] Đọc 9 file `progress/phase-*.md` (857 dòng tổng)
- [x] Trích blocker/deviation thật vào "Session Log" trong `implementation.md` (4 dòng — schema outbox version, package naming, CategoryAttributeAssignment cascade, V2 migration fix)
- [x] Xoá thư mục `progress/`
- [x] Không tạo `design.md` riêng — domain đã đủ ở `service/catalog-service/service.md`, feature này chỉ cần `implementation.md` (đúng như dự đoán)
- [x] **Phát hiện ngoài dự kiến, quan trọng:** checklist Phase 4-7 trong doc gốc toàn bộ chưa tick (Status: TODO) nhưng code thật (`services/catalog-service/.../domain/{product,variant}/`) đã có đầy đủ class + đã compile. Doc hoàn toàn không phản ánh đúng hiện trạng — đã ghi cảnh báo rõ ràng đầu `implementation.md` thay vì âm thầm sửa Status thành DONE (không xác minh được mức độ hoàn thành thật của application/presentation layer + test)

### Verify
Không còn thư mục `progress/`; `implementation.md` có Session Log ngắn gọn (857 dòng → ~90 dòng). ✓

---

## Phase 9 — Dọn orphan ở root

**Status:** `DONE`

### Checklist
- [x] Đọc đầy đủ `docs/catalog-sku-spu.md` (315 dòng) — phần lớn stale/duplicate với `service.md` đã merge (Phase 4); phần giá trị thật KHÔNG có ở đâu khác: cross-BC impact chi tiết (Inventory/Search/Pricing/Cart/Order/Reporting) + Open Questions chưa chốt → tách thành file phụ mới `service/catalog-service/downstream-effects.md` (đúng tiêu chí file phụ: dài, ổn định, không fit template có sẵn) + thêm rule "không approval flow" vào Business Rules + trỏ từ `service.md`; xoá file gốc
- [x] Viết lại `docs/README.md` ngắn gọn — chỉ pointer tới `doc-structure.md` + `agent-workflow.md`

### Verify
Không còn file `.md` lạc ngoài 3 tier ở `docs/` root, trừ `README.md` và `DOCS-RESTRUCTURE.md` (file plan tạm, xoá ở cuối). ✓

---

## Phase 10 — Fix discoverability file phụ

**Status:** `DONE`

### Checklist
- [x] `service/catalog-service/service.md` — đã trỏ `cache.md` + `downstream-effects.md` (làm ở Phase 4/9)
- [x] `service/inventory-service/service.md` — thêm mục "Tài liệu liên quan" trỏ `data.md` + `flashsale.md`
- [x] `service/order-service/` — hiện chỉ có `implementation.md`, chưa có `service.md`/`data.md` — ghi nhận gap, không tạo ngay (cần đọc code thật để viết đúng, ngoài scope đợt dọn doc)
- [x] **Phát hiện thêm ngoài checklist gốc:** `inventory-service/service.md` §Use Cases tham gia có 3 link trỏ `feature/checkout-saga/design.md` và `feature/limited-offer/design.md` — **cả 2 feature này không tồn tại** trong `docs/feature/`. Không tự sửa vì đây là quyết định nội dung (có thể `place-order` đã là "checkout-saga" dưới tên khác, hoặc 2 feature này thật sự chưa được viết) — cần user quyết định hướng, ghi nhận vào báo cáo cuối

### Verify
Mọi file phụ trong `service/{name}/` được ít nhất 1 dòng trong `service.md` trỏ tới. Broken link tới feature chưa tồn tại — đã ghi nhận, chưa xử lý (cần quyết định nội dung).

---

## Phase 11 — Final sweep

**Status:** `DONE`

### Checklist
- [x] Xoá `docs/design/` (chỉ còn 2 `.gitkeep` rỗng, đã rỗng thật sau Phase 4-7)
- [x] Grep path cũ toàn `docs/` — tìm thêm 3 chỗ ngoài dự kiến: `spring-security-login-mfa-bff.md` + `spring-security-logout-bff.md` (trỏ `docs/design/features/user-login/` — tên cũ "user-login" khác tên folder thật "user-auth"), `adr/010-order-crud-not-event-sourcing.md` (trỏ `docs/design/features/payment-checkout/implementation.md`) — đã sửa cả 3
- [x] Đối chiếu `agent-workflow.md` reading-list — verify từng path bằng lệnh test tồn tại thật, cả 9 path đều OK

### Verify
`docs/design/` không còn tồn tại; grep path cũ trong toàn `docs/` chỉ còn match tự-tham-chiếu trong chính file plan này. ✓

---

## Session Log

_(ghi khi có blocker/deviation thật so với plan trên — không ghi routine progress)_

- **2026-08-04:** Toàn bộ 12 phase (0-11) hoàn thành trong 1 phiên. Bug tự phát hiện ở Phase 11 sweep: Phase 7 đã nhúng nội dung 3 file `sequence.puml` (customer-registration, personal-info, place-order) vào `design.md` nhưng quên xoá file `.puml` gốc — `git mv` khi chuyển thư mục đã mang theo file rỗng-về-mặt-chức-năng này. Phát hiện qua `find docs -name "*.puml"` ở bước verify cuối, đã xoá cả 3. Bài học: bước "xoá file .puml" trong checklist nhúng diagram cần tách thành 1 dòng riêng, không gộp ngầm vào "nhúng nội dung" — dễ quên khi làm nhiều feature liên tiếp.
- **2026-08-04:** Phát hiện 2 gap nội dung: (1) `inventory-service/service.md` link chết tới `feature/checkout-saga/` + `feature/limited-offer/` — đã sửa: `checkout-saga` trỏ về `place-order` (cùng nội dung Saga order/inventory/payment/fulfillment, tên kỹ thuật khác), `limited-offer` trỏ `flashsale.md` (chưa có feature doc cross-service riêng, nội dung kỹ thuật đã có ở đây). (2) `service/order-service/` thiếu `service.md`/`data.md` — **để lại, chưa làm** — cần đọc code thật, không phải việc di chuyển file, để phiên riêng.
