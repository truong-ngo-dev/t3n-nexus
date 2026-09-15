package vn.t3nexus.scheduler.domain.schedule;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public final class ScheduleException {

    private ScheduleException() {}

    public static DomainException missingDueAt() {
        return new DomainException(ScheduleErrorCode.SCHEDULE_MISSING_DUE_AT);
    }

    public static DomainException missingCronExpression() {
        return new DomainException(ScheduleErrorCode.SCHEDULE_MISSING_CRON_EXPRESSION);
    }

    public static DomainException invalidCronExpression(String cron) {
        return new DomainException(ScheduleErrorCode.SCHEDULE_INVALID_CRON_EXPRESSION,
                "Invalid cron expression: %s".formatted(cron));
    }

    public static DomainException missingTimezone() {
        return new DomainException(ScheduleErrorCode.SCHEDULE_MISSING_TIMEZONE);
    }

    public static DomainException invalidTimezone(String timezone) {
        return new DomainException(ScheduleErrorCode.SCHEDULE_INVALID_TIMEZONE,
                "Invalid timezone: %s".formatted(timezone));
    }
}
