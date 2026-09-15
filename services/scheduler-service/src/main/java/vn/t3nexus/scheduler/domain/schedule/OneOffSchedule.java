package vn.t3nexus.scheduler.domain.schedule;

import java.time.Instant;
import java.util.Optional;

/**
 * Value Object — lịch chạy đúng 1 lần. {@code nextFireTime} trả {@code dueAt} nếu chưa qua hạn, rỗng
 * nếu đã qua — rỗng nghĩa là vòng đời kết thúc (Schedule đã hết vòng đời, khác với Recurring không bao
 * giờ rỗng).
 */
public record OneOffSchedule(Instant dueAt) implements Schedule {

    public OneOffSchedule {
        if (dueAt == null) throw ScheduleException.missingDueAt();
    }

    /** {@code cronCalculator} không dùng tới — one-off không cần cron. */
    @Override
    public Optional<Instant> nextFireTime(Instant after, CronCalculator cronCalculator) {
        return after.isBefore(dueAt) ? Optional.of(dueAt) : Optional.empty();
    }

    @Override
    public boolean isRecurring() {
        return false;
    }
}
