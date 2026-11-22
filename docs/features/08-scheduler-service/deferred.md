# Deferred — scheduler-service

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác (hoặc không làm ở scope hiện tại).

---

## 1. Circuit-breaker fallback khi Redis lỗi liên tục — chặn bởi quyết định pipeline (2026-09-05)

**Làm khi nào:** Nếu cần production-grade availability cho lớp `DueItemFinder`, hoặc khi muốn rehearsal Profile 2 đi sâu hơn.

**Vấn đề:**
Thiết kế hiện tại (pipeline Aniket-style — xem `design.md`) chỉ có **1 nguồn duy nhất** chạy Claim+Trigger: Index Poller đọc `DueItemFinder` (Redis). Nếu Redis down **hoàn toàn** (không chỉ mất 1 entry), Index Poller không còn gì để đọc → fire dừng hẳn tới khi Redis phục hồi. `IndexReconciler` (Postgres) vẫn chạy nhưng chỉ ghi vào index, không tự fire — không cứu được tình huống này. Đây là đánh đổi có chủ đích so với phương án "2 loop độc lập cùng fire" đã cân nhắc và bỏ (ở đó DB tự fire được kể cả khi Redis chết hẳn) — xem `design.md` §Failure Scenarios.

**Đề xuất:** thêm circuit-breaker (VD Resilience4j) quanh `RedisDueItemFinderAdapter.pollDue()` — N lần lỗi liên tiếp (hoặc timeout) → tạm fallback Index Poller sang gọi thẳng `ScheduleStore.findDueBefore()` (giống hành vi Phase 3), tự động quay lại đọc Redis khi circuit đóng lại. Chưa làm — chấp nhận rủi ro ở cardinality hiện tại (4 job, jitter phút, Redis outage hiếm/ngắn).

**Files liên quan:** `infrastructure/scheduling/IndexPoller`, `infrastructure/adapter/.../RedisDueItemFinderAdapter` (Phase 4, chưa code).

---

## 2. Time-bucket + segment sharding cho cardinality cao — chặn bởi cardinality thực tế chưa tăng

**Làm khi nào:** Sau khi cardinality thực tế tăng đủ lớn (nhiều domain/tenant tự tạo job qua REST Admin — Phase 5) để query poll/index trở thành bottleneck cho `poll_time` — xem điều kiện kích hoạt `DueItemFinder` ở Knowledge base `5. core-components.md` §3.3.

**Vấn đề:**
Knowledge base Profile 2 (`distributed-systems/components/job-scheduling/7. profile-distributed-poll.md`) mô tả kỹ thuật **time-bucket partitioning** (tách bảng định nghĩa job khỏi index theo thời gian) + **sub-shard bằng cột `segment`** khi 1 time-bucket vẫn quá tải cho 1 tác nhân — cả 2 case neo (Mayil Bayramov, Aniket Yadav) đều dùng kỹ thuật này ở scale hàng chục triệu task/ngày. Ở 4 job cố định, áp dụng kỹ thuật này là diễn kịch (không có volume thật để chứng minh nhu cầu) — cố tình không build.

**Đề xuất:** nếu sau này cardinality tăng thật, cân nhắc tách `scheduled_job` (định nghĩa) khỏi 1 cấu trúc index riêng theo time-bucket (partition/table riêng theo phút/giờ); thêm cột `segment` nếu 1 bucket vẫn quá tải 1 instance. Batch size tham khảo: 500 job/lần (nguồn LinkedIn, xem Knowledge base). Chưa thiết kế field/schema cụ thể.

**Files liên quan:** `scheduled_job` (`data.md`), `CreateScheduledJobHandler` (Phase 5).

---

## 3. Audit log lịch sử thay đổi job — đã note trong `service.md`, chưa thiết kế chi tiết

**Làm khi nào:** Sau khi Admin REST API (Start/Stop/Edit) chạy thật và cần truy vết "ai đổi gì lúc nào".

**Vấn đề:**
`service.md` §Commands đã ghi nhận gap này ("còn treo") — khuyến nghị 1 aggregate riêng `ScheduledJobAuditLog` (append-only, ghi qua Event Handler phản ứng theo domain event của `ScheduledJob`, không phải trong chính Handler chính). Chưa thiết kế field/event cụ thể — và hiện `ScheduledJob` chưa raise domain event nào cho `start()`/`stop()`/`edit()` (chỉ `fire()` qua `ScheduledJobInstance.dispatch()` raise), nên cần quyết định thêm event trước khi audit log khả thi.

**Files liên quan:** `service.md` §Commands, `ScheduledJob` aggregate.

---

## 4. Retry-khi-fail cho `ScheduledJobInstance` — cố tình để ngỏ

**Làm khi nào:** Khi có nhu cầu nghiệp vụ thật cần tự động retry job fail (hiện tại: consumer tự chịu trách nhiệm retry nội bộ, hoặc Admin tự `start()` lại thủ công).

**Vấn đề:**
`service.md` §Callback đã ghi rõ: không fuse vào `ScheduledJob.fire()` — đúng khuyến nghị Knowledge base ("Retry — vì sao cố tình không phải bước thứ 6": policy biến thiên mạnh — số lần, delay cố định hay backoff, có DLQ hay không — không có 1 hình dạng "đúng" để cố định thành primitive). Nếu làm sau này, phải là cơ chế tách biệt đọc `ScheduledJobInstance.status`/`failureReason`.

**Files liên quan:** `service.md` §Callback, `ScheduledJobInstance`.
