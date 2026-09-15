package vn.t3nexus.scheduler.domain.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Value Object — lịch lặp lại theo cron expression. {@code nextFireTime} không bao giờ rỗng — cron
 * luôn có lần kế tiếp.
 * <br>Không tự parse cron nữa — delegate hoàn toàn cho {@link CronCalculator} (port, xem javadoc của
 * nó cho lý do truyền vào thay vì resolve trước ở Handler). Compact constructor chỉ check cấu trúc
 * (null/blank) — **không** validate cú pháp cron cụ thể ở đây nữa, vì việc đó cần 1 cron engine mà VO
 * (record) không thể giữ instance injected. Cú pháp cron sai sẽ lộ ra ngay lần gọi
 * {@code nextFireTime()} đầu tiên (luôn xảy ra ngay trong {@code ScheduledJob.create()}) — adapter
 * implement {@code CronCalculator} có trách nhiệm bắt lỗi engine gốc và ném lại
 * {@link ScheduleException#invalidCronExpression(String)} (xem
 * {@code infrastructure/adapter/service/cron/SpringCronCalculatorAdapter}).
 */
public record RecurringSchedule(String cron, ZoneId zone) implements Schedule {

    public RecurringSchedule {
        if (cron == null || cron.isBlank()) throw ScheduleException.missingCronExpression();
        if (zone == null) throw ScheduleException.missingTimezone();
    }

    @Override
    public Optional<Instant> nextFireTime(Instant after, CronCalculator cronCalculator) {
        return cronCalculator.nextFireTime(cron, zone, after);
    }

    @Override
    public boolean isRecurring() {
        return true;
    }
}
