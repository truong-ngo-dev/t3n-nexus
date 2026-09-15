package vn.t3nexus.scheduler.domain.schedule;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum ScheduleErrorCode implements ErrorCode {

    SCHEDULE_MISSING_DUE_AT("SCHEDULE_MISSING_DUE_AT", "OneOffSchedule requires a due date", "error.schedule.missing_due_at", 422),
    SCHEDULE_MISSING_CRON_EXPRESSION("SCHEDULE_MISSING_CRON_EXPRESSION", "RecurringSchedule requires a cron expression", "error.schedule.missing_cron_expression", 422),
    SCHEDULE_INVALID_CRON_EXPRESSION("SCHEDULE_INVALID_CRON_EXPRESSION", "Invalid cron expression", "error.schedule.invalid_cron_expression", 422),
    SCHEDULE_MISSING_TIMEZONE("SCHEDULE_MISSING_TIMEZONE", "RecurringSchedule requires a timezone", "error.schedule.missing_timezone", 422),
    SCHEDULE_INVALID_TIMEZONE("SCHEDULE_INVALID_TIMEZONE", "Invalid IANA timezone id", "error.schedule.invalid_timezone", 422);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    ScheduleErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey() { return messageKey; }
    @Override public int httpStatus() { return httpStatus; }
}
