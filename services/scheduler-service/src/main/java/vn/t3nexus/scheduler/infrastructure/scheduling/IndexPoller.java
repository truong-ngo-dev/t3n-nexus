package vn.t3nexus.scheduler.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.t3nexus.scheduler.application.scheduled_job.fire.FireScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.DueItemFinder;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;

import java.time.Instant;
import java.util.List;

/**
 * Nhịp đập của bước <b>Poll</b> — nguồn DUY NHẤT chạy Claim + Trigger trong mô hình pipeline (Phase 4,
 * xem {@code implementation.md}).
 *
 * <h2>"Listener từ JVM scheduler" nghĩa là gì</h2>
 * Không có event bus, không có message nào ở đây. {@code @Scheduled} đăng ký {@link #poll()} với
 * {@code TaskScheduler} của Spring — mặc định là 1
 * {@link java.util.concurrent.ScheduledThreadPoolExecutor} chạy <b>trong chính tiến trình JVM này</b>.
 * JVM giữ 1 hàng đợi delay-queue; tới hạn thì rút 1 thread trong pool ra gọi {@link #poll()}. Đây là
 * toàn bộ "đồng hồ" của scheduler: mọi job đến hạn đều được phát hiện bởi vòng lặp poll này, KHÔNG phải
 * bởi 1 timer riêng cho từng job (mô hình timer-per-job chỉ xuất hiện khi cần precision sub-giây — ngoài
 * scope hiện tại).
 *
 * <h2>{@code fixedDelay}, không phải {@code fixedRate}</h2>
 * Lần chạy kế tiếp bắt đầu sau khi lần này <i>kết thúc</i> đúng {@code scheduler.index-poller.fixed-delay-ms}.
 * Chọn {@code fixedDelay} để 1 tick chạy lâu (Redis chậm, batch lớn) không làm các tick dồn toa chồng
 * lên nhau — hệ tự điều tiết. Đánh đổi: chu kỳ thực tế {@code Δ ≈ fixed-delay + thời-gian-xử-lý-tick},
 * hơi trôi so với danh nghĩa; chấp nhận được vì ngân sách jitter hiện tại tính bằng phút. Hệ quả:
 * {@code poll_delay} tối thiểu của mọi job ≈ {@code Δ} này (sàn không phá được bằng cách chỉnh poll —
 * muốn nhỏ hơn phải đổi mô hình).
 *
 * <h2>Vì sao cần {@code spring.task.scheduling.pool.size} ≥ 2</h2>
 * {@link IndexReconciler} cũng là 1 {@code @Scheduled} trong cùng service. Pool mặc định của Spring chỉ
 * 1 thread — reconciler quét DB lâu sẽ chặn poller (và ngược lại). Tách tối thiểu 2 thread để 2 nhịp
 * độc lập.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexPoller {

    private final DueItemFinder dueItemFinder;
    private final FireScheduledJob fireScheduledJob;

    @Value("${scheduler.index-poller.batch-limit}")
    private int batchLimit;

    @Scheduled(fixedDelayString = "${scheduler.index-poller.fixed-delay-ms}")
    public void poll() {
        Instant now = Instant.now();

        // ── Bước POLL ─────────────────────────────────────────────────────────────────────────────
        // Claim atomic tối đa `batchLimit` id có dueAt <= now, LẤY RA KHỎI index luôn: pollDue() =
        // ZRANGEBYSCORE + ZREM trong 1 Lua script (xem RedisDueItemFinderAdapter). Nhờ Redis đơn
        // luồng, 2 instance scheduler-service chạy HA gọi đồng thời KHÔNG thể cùng nhận trùng 1 id.
        //
        // Kết quả vẫn chỉ là GỢI Ý, không phải lệnh: giữa lúc id nằm trong ZSET và lúc tick này đọc,
        // job có thể vừa bị stop(), hoặc nextFireAt vừa bị dời. Vì vậy bước sau BẮT BUỘC re-verify
        // qua findByIdForUpdate + guard nextFireAt <= now (Flow D, Knowledge base 5. core-components).
        List<ScheduledJobId> dueIds;
        try {
            dueIds = dueItemFinder.pollDue(now, batchLimit);
        } catch (RuntimeException e) {
            // Redis down / timeout: bỏ tick này, tick sau thử lại. KHÔNG để exception thoát ra —
            // với fixedDelay Spring vẫn reschedule, nhưng nuốt ở đây để log gọn + sau này gắn counter.
            // (Phase 4.5: counter scheduler.poll.error++.)
            log.error("[IndexPoller] pollDue failed, skip tick. reason={}", e.toString());
            return;
        }

        if (dueIds.isEmpty()) {
//            log.info("[IndexPoller] nothing due at {}", now);
            return;
        }
        log.info("[IndexPoller] claimed {} due id(s) at {}", dueIds.size(), now);

        // ── Bước CLAIM + TRIGGER ─────────────────────────────────────────────────────────────────
        // Mỗi id = 1 lần gọi FireScheduledJob.handle(), MỖI LẦN 1 TRANSACTION RIÊNG (không gộp batch):
        // 1 job lost-race / lỗi bất kỳ không được kéo các id đã claim khác trong tick này — chúng đã bị
        // ZREM khỏi index, nếu tick chết giữa chừng thì mất tới sweep kế của IndexReconciler.
        //
        // FireScheduledJob tự nuốt DomainException(SCHEDULED_JOB_INVALID_TRANSITION) bên trong (race
        // re-verify). OptimisticLockingFailureException từ @Version trên scheduled_job thì KHÔNG — nó
        // thoát ra tới đây.
        for (ScheduledJobId id : dueIds) {
            try {
                fireScheduledJob.handle(new FireScheduledJob.Command(id.getValue()));
            } catch (OptimisticLockingFailureException e) {
                // Nguồn khác (instance HA khác) đã fire xong job này giữa pollDue và handle. Benign —
                // re-verify trong ScheduledJob.fire() đã chặn double-fire. (Phase 4.5: scheduler.claim.lost++)
                log.error("[IndexPoller] lost claim race, skip. scheduledJobId={}", id.getValue());
            } catch (RuntimeException e) {
                // Lỗi thật của 1 job (bug, DB timeout...): log rồi đi tiếp để các id còn lại vẫn chạy.
                // (Phase 4.5: scheduler.fire.error++.)
                log.error("[IndexPoller] fire failed, skip id. scheduledJobId={}, reason={}", id.getValue(), e.toString());
            }
        }
    }
}
