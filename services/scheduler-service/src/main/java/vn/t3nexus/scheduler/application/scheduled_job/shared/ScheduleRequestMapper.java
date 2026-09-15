package vn.t3nexus.scheduler.application.scheduled_job.shared;

import vn.t3nexus.scheduler.domain.schedule.OneOffSchedule;
import vn.t3nexus.scheduler.domain.schedule.RecurringSchedule;
import vn.t3nexus.scheduler.domain.schedule.Schedule;
import vn.t3nexus.scheduler.domain.schedule.ScheduleException;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Ranh giới mapping input phẳng (REST request) sang {@link Schedule} đa hình — dùng chung cho
 * {@code CreateScheduledJob} và {@code EditScheduledJob}, 2 nơi duy nhất nhận input dạng
 * {@code scheduleType}/{@code dueAt}/{@code cronExpression}/{@code timezone} rời rạc. Nhánh
 * {@code switch (scheduleType)} chỉ tồn tại đúng ở đây — KHÔNG phải logic domain (khác các nhánh
 * {@code if (recurring)} đã loại bỏ khỏi domain nhờ {@code Schedule.nextFireTime()} đa hình).
 */
public final class ScheduleRequestMapper {

    private ScheduleRequestMapper() {}

    public static Schedule toSchedule(String scheduleType, Instant dueAt, String cronExpression, String timezone) {
        return switch (scheduleType) {
            case "ONE_OFF" -> new OneOffSchedule(dueAt);
            case "RECURRING" -> new RecurringSchedule(cronExpression, toZoneId(timezone));
            default -> throw ScheduledJobException.invalidScheduleType(scheduleType);
        };
    }

    /** Dịch {@code DateTimeException} của JDK thành domain exception — cùng nguyên tắc không leak raw
     * exception ra khỏi domain đã áp dụng cho cron ở {@code SpringCronCalculatorAdapter}. */
    private static ZoneId toZoneId(String timezone) {
        if (timezone == null) throw ScheduleException.missingTimezone();
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw ScheduleException.invalidTimezone(timezone);
        }
    }
}
