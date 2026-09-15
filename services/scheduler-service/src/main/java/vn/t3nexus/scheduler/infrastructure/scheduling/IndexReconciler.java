package vn.t3nexus.scheduler.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.t3nexus.scheduler.domain.scheduled_job.DueItemFinder;
import vn.t3nexus.scheduler.domain.scheduled_job.DueJobRef;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reconciliation sweep — nạp lại {@code DueItemFinder} (index tăng tốc) từ Postgres (nguồn sự thật).
 * <b>KHÔNG bao giờ fire.</b>
 *
 * <h2>Vai trò trong pipeline</h2>
 * Nhánh ghi index <i>chính</i> là event-driven, best-effort: mỗi {@code start()} / {@code stop()} /
 * {@code fire()} recurring gọi {@code DueItemFinder.index()} / {@code deindex()} <b>sau khi transaction
 * DB đã commit</b>, không atomic với nó. Nên nhánh đó có thể trượt: Redis chớp tắt đúng lúc gọi, process
 * chết giữa commit và ghi index, entry bị evict... Sweep này chạy chậm (2–5 phút), quét toàn bộ job đang
 * {@code RUNNING} sắp đến hạn và ghi đè lại index <b>vô điều kiện</b>, kéo index hội tụ về đúng trạng
 * thái của DB.
 *
 * <p>Đây chính là "≥1 poller dùng {@code ScheduleStore} song song" mà Knowledge base
 * {@code distributed-systems/components/job-scheduling/5. core-components.md} (Flow B) yêu cầu — khác
 * biệt duy nhất: ở đây "dùng {@code ScheduleStore}" nghĩa là <b>nạp lại index</b>, không tự chạy
 * Claim + Trigger trực tiếp trên nó.
 *
 * <h2>Vì sao KHÔNG fire ở đây</h2>
 * Nếu sweep vừa nạp index vừa fire thì có 2 nguồn Claim+Trigger cho cùng 1 outcome ({@link IndexPoller}
 * và class này) — buộc phải cho chúng "biết nhau" để khỏi trùng. Pipeline cắt bỏ hẳn vấn đề đó:
 * {@link IndexPoller} là nguồn fire duy nhất, class này chỉ bơm dữ liệu vào index cho nó đọc. Đánh đổi
 * đã chấp nhận: Redis down <i>hoàn toàn</i> ⇒ fire dừng tới khi phục hồi (sweep vẫn chạy nhưng ghi vào
 * index không ai đọc) — xem {@code design.md} §Failure Scenarios.
 *
 * <h2>{@code lookahead}</h2>
 * Không quét đúng {@code next_fire_at <= now} mà quét tới {@code now + lookahead}, để job sắp đến hạn
 * kịp có mặt trong index <i>trước</i> khi {@link IndexPoller} cần — bịt khe hở "job đến hạn ngay giữa 2
 * lần sweep, đúng lúc nhánh event-driven vừa trượt".
 *
 * <h2>Không lock, không re-verify</h2>
 * Đọc thẳng {@code ScheduleStore}, không {@code FOR UPDATE}, không guard: sai sót 1 nhịp sẽ được nhịp
 * sau (hoặc chính nhánh event-driven) sửa. Đây là điểm khác cốt lõi với {@link IndexPoller} — cái đó
 * fire nên bắt buộc lock + re-verify.
 *
 * <h2>Chạy trên MỌI instance, đồng thời, KHÔNG điều phối — và vì sao không cần guard</h2>
 * {@code reconcile()} là {@code @Scheduled} nên chạy độc lập trên từng instance {@code scheduler-service}.
 * KHÔNG có leader election, ShedLock, hay lock nào để "chỉ 1 instance quét". An toàn vì:
 * <ul>
 *   <li><b>{@code SELECT} là read thuần</b> — không {@code FOR UPDATE}/{@code SKIP LOCKED}. Postgres MVCC:
 *       reader không chặn reader/writer, không row lock nào để tranh. N instance quét cùng lúc = N
 *       {@code SELECT} song song, mỗi cái trên snapshot riêng. Không có gì để guard ở bước đọc.</li>
 *   <li><b>Bước ghi là {@code ZADD} idempotent + commutative</b> — mọi instance đọc {@code next_fire_at}
 *       từ cùng nguồn (Postgres) nên ghi cùng score; chạy 1 lần hay N lần cho cùng kết quả. Cố ý KHÔNG
 *       dùng {@code SKIP LOCKED} ở scan: ta MUỐN mọi instance có khả năng nạp lại mọi entry (dư thừa
 *       chính là mục đích của lưới an toàn), không phải chia tập con rời nhau.</li>
 *   <li><b>Snapshot lệch nhau giữa các instance không sao</b> — A đọc trước 1 lần fire, B đọc sau: A
 *       {@code ZADD id <score cũ>}, B {@code ZADD id <score mới>}, cái land sau thắng. Nếu score cũ thắng
 *       → {@link IndexPoller} pop sớm → {@code findByIdForUpdate} re-verify {@code nextFireAt <= now} fail
 *       → no-op, và {@code pollDue} tự {@code ZREM} dọn. Sweep kế / after-commit {@code index()} của fire
 *       path ghi đè lại score đúng. Tự lành trong ≤ 1 chu kỳ sweep — đúng bằng ngân sách
 *       {@code lookahead = 2× interval}.</li>
 *   <li><b>{@code ZADD} không bao giờ xoá</b> → concurrent reconciler KHÔNG thể gây missed-fire; và vì
 *       không fire ở đây nên cũng KHÔNG thể gây double-fire. Guard correctness thật (claim {@code ZREM},
 *       row lock, re-verify {@code nextFireAt <= now}, {@code @Version}) nằm hết ở fire path, class này
 *       không chạm tới.</li>
 * </ul>
 * Thứ <i>duy nhất</i> có thể tối ưu (không phải guard): N {@code SELECT} giống hệt đập DB cùng khoảnh
 * khắc. Ở cardinality hiện tại (≤ vài chục row) không đáng. Khi cần: jitter {@code initialDelay} per
 * instance / ShedLock cho 1 sweeper / partition theo hash range — xem {@code deferred.md}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexReconciler {

    private final ScheduledJobRepository scheduledJobRepository;
    private final DueItemFinder dueItemFinder;

    @Value("${scheduler.index-reconciler.lookahead-seconds}")
    private long lookaheadSeconds;

    @Value("${scheduler.index-reconciler.batch-limit}")
    private int batchLimit;

    @Scheduled(
            fixedDelayString   = "${scheduler.index-reconciler.fixed-delay-ms}",
            initialDelayString = "${scheduler.index-reconciler.initial-delay-ms}"
    )
    public void reconcile() {
        Instant horizon = Instant.now().plus(Duration.ofSeconds(lookaheadSeconds));

        // ── Bước POLL ─────────────────────────────────────────────────────────────────────────────
        // Lấy các job RUNNING sẽ đến hạn trong cửa sổ (.., now + lookahead], kèm nextFireAt. Read thuần,
        // không lock — an toàn khi mọi instance quét đồng thời (xem javadoc class, phần concurrent).
        List<DueJobRef> due;
        try {
            due = scheduledJobRepository.findDueBefore(horizon, batchLimit);
        } catch (RuntimeException e) {
            log.warn("[IndexReconciler] findDueBefore failed, skip sweep. reason={}", e.toString());
            return;
        }

        if (due.isEmpty()) {
            log.trace("[IndexReconciler] sweep: nothing due before {}", horizon);
            return;
        }

        // ── Bước GHI INDEX ──────────────────────────────────────────────────────────────────────
        // ZADD lại từng (id, dueAt) — idempotent, ghi đè vô điều kiện (không check Redis đang có gì).
        // dueItemFinder.index() ở đây gọi NGOÀI transaction nên chạy ngay, không hoãn (xem contract
        // DueItemFinder). Lỗi Redis từng entry được nuốt trong adapter (best-effort) — 1 entry hỏng
        // không chặn các entry còn lại; sweep kế thử lại.
        // TUYỆT ĐỐI không gọi FireScheduledJob ở đây — claim + fire là việc riêng của IndexPoller.
        for (DueJobRef ref : due) {
            dueItemFinder.index(ref.id(), ref.dueAt());
        }
        log.info("[IndexReconciler] sweep: re-indexed {} RUNNING job(s) due before {}", due.size(), horizon);
    }
}
