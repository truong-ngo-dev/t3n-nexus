package vn.t3nexus.scheduler.domain.scheduled_job;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum ScheduledJobErrorCode implements ErrorCode {

    SCHEDULED_JOB_NOT_FOUND("SCHEDULED_JOB_NOT_FOUND", "Scheduled job not found", "error.scheduled_job.not_found", 404),
    SCHEDULED_JOB_ALREADY_DUE("SCHEDULED_JOB_ALREADY_DUE", "Schedule has no future fire time", "error.scheduled_job.already_due", 422),
    SCHEDULED_JOB_INVALID_TRANSITION("SCHEDULED_JOB_INVALID_TRANSITION", "Invalid scheduled job state transition", "error.scheduled_job.invalid_transition", 422),
    SCHEDULED_JOB_INVALID_SCHEDULE_TYPE("SCHEDULED_JOB_INVALID_SCHEDULE_TYPE", "scheduleType must be ONE_OFF or RECURRING", "error.scheduled_job.invalid_schedule_type", 422);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    ScheduledJobErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
