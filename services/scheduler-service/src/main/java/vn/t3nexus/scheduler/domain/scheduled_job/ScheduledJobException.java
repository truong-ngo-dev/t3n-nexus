package vn.t3nexus.scheduler.domain.scheduled_job;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public final class ScheduledJobException {

    private ScheduledJobException() {}

    public static DomainException notFound() {
        return new DomainException(ScheduledJobErrorCode.SCHEDULED_JOB_NOT_FOUND);
    }

    public static DomainException alreadyDue() {
        return new DomainException(ScheduledJobErrorCode.SCHEDULED_JOB_ALREADY_DUE);
    }

    public static DomainException invalidTransition(ScheduledJobStatus current, String action) {
        return new DomainException(ScheduledJobErrorCode.SCHEDULED_JOB_INVALID_TRANSITION,
                "Cannot %s scheduled job in status %s".formatted(action, current));
    }

    public static DomainException invalidScheduleType(String scheduleType) {
        return new DomainException(ScheduledJobErrorCode.SCHEDULED_JOB_INVALID_SCHEDULE_TYPE,
                "scheduleType phải là ONE_OFF hoặc RECURRING, nhận: %s".formatted(scheduleType));
    }
}
