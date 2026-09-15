package vn.t3nexus.scheduler.application.scheduled_job.shared;

import vn.t3nexus.scheduler.domain.schedule.OneOffSchedule;
import vn.t3nexus.scheduler.domain.schedule.RecurringSchedule;
import vn.t3nexus.scheduler.domain.schedule.Schedule;

import java.time.Instant;

/**
 * Chiều ngược lại {@link ScheduleRequestMapper} — làm phẳng {@link Schedule} đa hình thành 4 field rời
 * rạc cho response REST ({@code GetScheduledJob}/{@code ListScheduledJobs} qua {@link ScheduledJobView}).
 * Cùng nguyên tắc: switch chỉ tồn tại ở tầng biên (application), không lọt vào domain.
 */
public record ScheduleView(String scheduleType, Instant dueAt, String cronExpression, String timezone) {

    public static ScheduleView from(Schedule schedule) {
        return switch (schedule) {
            case OneOffSchedule oneOff -> new ScheduleView("ONE_OFF", oneOff.dueAt(), null, null);
            case RecurringSchedule recurring ->
                    new ScheduleView("RECURRING", null, recurring.cron(), recurring.zone().getId());
        };
    }
}
