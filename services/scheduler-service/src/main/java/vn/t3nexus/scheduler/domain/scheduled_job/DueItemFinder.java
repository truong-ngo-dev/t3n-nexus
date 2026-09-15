package vn.t3nexus.scheduler.domain.scheduled_job;

import java.time.Instant;
import java.util.List;

/**
 * Port tăng tốc tìm {@link ScheduledJob} đến hạn — <b>KHÔNG phải Repository</b> (cố tình không extend
 * {@code Repository<T,ID>}: không giữ aggregate, không có invariant, chỉ index {@code id -> dueAt}) và
 * <b>KHÔNG phải Domain Service</b> (không chứa logic nghiệp vụ nào — thuần I/O, thuộc nhóm driven port
 * cùng hạng với {@link ScheduledJobFireService}'s collaborator kiểu {@code TriggerPublisher}, không phải
 * {@code ScheduledJobFireService}).
 *
 * <p><b>Component tuỳ chọn, có điều kiện</b> — không phải model lõi. Chỉ cần thiết khi query trực tiếp
 * {@link ScheduledJobRepository#findDueBefore} trở thành bottleneck cho {@code poll_time} (Knowledge base
 * {@code distributed-systems/components/job-scheduling/5. core-components.md} §3.3) — điều kiện này CHƯA
 * xảy ra ở cardinality hiện tại (4 job cố định). Impl hiện có (Redis ZSET) được thêm có chủ đích để
 * rehearse pattern Profile 2 cho mục đích học tập/portfolio (xem {@code design.md} §NFR Assessment), không
 * phải nhu cầu performance thật.
 *
 * <p><b>Không durable</b> — mất dữ liệu ở đây không sao. {@link ScheduledJobRepository} luôn là nguồn sự
 * thật; {@code IndexReconciler} (Application layer, quét {@code ScheduledJobRepository#findDueBefore}
 * định kỳ rồi gọi lại {@link #index}) tự bù mọi drift. Vì vậy mọi implementation của {@link #index}/
 * {@link #deindex} PHẢI idempotent và an toàn khi gọi lặp lại vô điều kiện.
 *
 * <p><b>Thời điểm áp dụng & xử lý lỗi — guarantee của contract, không phải việc của caller.</b>
 * {@link #index}/{@link #deindex} khi được gọi <i>trong một transaction</i> thì thao tác ghi được
 * <b>hoãn tới sau khi transaction đó commit</b> (rollback → không ghi gì); gọi <i>ngoài transaction</i>
 * (VD {@code IndexReconciler}, test) → ghi ngay. Lỗi ở tầng ghi (Redis down/timeout) được implementation
 * <b>nuốt</b> (log rồi bỏ qua) — KHÔNG ném ra, KHÔNG làm rollback transaction của caller. Command Handler
 * chỉ cần gọi {@code index}/{@code deindex} như một dòng lệnh bình thường trong flow; việc canh commit và
 * best-effort nằm hết ở implementation.
 */
public interface DueItemFinder {

    /**
     * Ghi/đè {@code id -> dueAt} vào cấu trúc tăng tốc. <b>Idempotent</b> — gọi lại với cùng {@code id}
     * luôn ghi đè bằng {@code dueAt} mới nhất, kể cả khi entry đã tồn tại đúng giá trị (no-op hiệu quả,
     * nhưng caller KHÔNG được tự check-rồi-skip trước khi gọi — {@code IndexReconciler} dựa vào tính chất
     * này để luôn gọi vô điều kiện cho mọi row quét được, không cần biết Redis đang có gì).
     *
     * <p><b>Best-effort, hoãn-sau-commit, KHÔNG atomic</b> với transaction ghi
     * {@link ScheduledJobRepository} — xem phần "Thời điểm áp dụng" ở javadoc của interface. Gọi bởi:
     * {@code StartScheduledJob}, {@code FireScheduledJob} (nhánh recurring vừa fire hoặc vừa
     * {@code skipMisfire()}), {@code IndexReconciler} (định kỳ, bù drift — gọi ngoài transaction nên ghi
     * ngay).
     */
    void index(ScheduledJobId id, Instant dueAt);

    /**
     * Xoá {@code id} khỏi cấu trúc tăng tốc. <b>Idempotent</b> — xoá 1 id không tồn tại là no-op, không
     * phải lỗi. Cùng nguyên tắc best-effort/hoãn-sau-commit/không atomic như {@link #index}. Gọi bởi:
     * {@code StopScheduledJob}, {@code EditScheduledJob} (cả 2 đưa job về PENDING),
     * {@code FireScheduledJob} (nhánh one-off vừa chuyển {@code COMPLETED}).
     */
    void deindex(ScheduledJobId id);

    /**
     * Trả về tối đa {@code limit} id có {@code dueAt <= asOf}. <b>Bắt buộc là 1 claim atomic</b> — 2 lời
     * gọi đồng thời (2 instance {@code scheduler-service} chạy HA) KHÔNG được cùng trả về 1 id (implementation
     * Redis dùng {@code ZRANGEBYSCORE}+{@code ZREM} gộp trong 1 Lua script, tận dụng tính đơn luồng của
     * Redis). Đây là ràng buộc correctness của contract, không phải chi tiết implementation — thiếu nó thì
     * 2 {@code IndexPoller} có thể cùng nhận 1 id, dồn hết việc chặn double-fire xuống re-verify (vẫn đúng,
     * nhưng tốn thêm round-trip DB vô ích).
     *
     * <p>Kết quả là <b>gợi ý, không phải lệnh</b> — {@code IndexPoller} PHẢI load lại qua
     * {@link ScheduledJobRepository#findByIdForUpdate} và kiểm tra {@code canProcess()} trước khi fire
     * (Flow D, Knowledge base {@code 5. core-components.md}). Không bao giờ là nguồn poll duy nhất khi
     * chưa có {@code IndexReconciler} chạy song song bù drift.
     *
     * <p><b>Áp dụng NGAY, không hoãn</b> — khác {@link #index}/{@link #deindex}. {@code IndexPoller} gọi
     * method này ngoài mọi transaction (nó là {@code @Scheduled}, không {@code @Transactional}), nên
     * không có commit nào để chờ.
     */
    List<ScheduledJobId> pollDue(Instant asOf, int limit);
}
