package vn.t3nexus.scheduler.domain.schedule;

import java.time.Instant;
import java.util.Optional;

/**
 * Domain concept riêng — dùng chung nếu BC khác trong scheduler-service cần (xem
 * docs/service/scheduler-service/service.md). 1 method {@code nextFireTime} xử lý được cả 2 loại nhờ
 * tính đa hình — không cần nhánh {@code if (recurring)} ở bất kỳ đâu khác trong hệ thống.
 */
public sealed interface Schedule permits OneOffSchedule, RecurringSchedule {

    /**
     * @param after         mốc thời gian tham chiếu (thường là "now")
     * @param cronCalculator port tính cron — {@link OneOffSchedule} bỏ qua hoàn toàn (không cần cron),
     *                      {@link RecurringSchedule} delegate toàn bộ việc tính toán. Xem
     *                      {@link CronCalculator} javadoc cho lý do truyền port thay vì resolve trước.
     * @return lần trigger kế tiếp sau {@code after}, rỗng nếu Schedule đã hết vòng đời (chỉ xảy ra với
     *         {@link OneOffSchedule} đã qua hạn — {@link RecurringSchedule} không bao giờ rỗng)
     */
    Optional<Instant> nextFireTime(Instant after, CronCalculator cronCalculator);

    boolean isRecurring();
}
