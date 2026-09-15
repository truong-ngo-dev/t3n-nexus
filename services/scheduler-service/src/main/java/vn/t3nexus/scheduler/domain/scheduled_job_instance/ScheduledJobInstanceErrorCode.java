package vn.t3nexus.scheduler.domain.scheduled_job_instance;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum ScheduledJobInstanceErrorCode implements ErrorCode {

    SCHEDULED_JOB_INSTANCE_NOT_FOUND("SCHEDULED_JOB_INSTANCE_NOT_FOUND", "Scheduled job instance not found", "error.scheduled_job_instance.not_found", 404);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    ScheduledJobInstanceErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
