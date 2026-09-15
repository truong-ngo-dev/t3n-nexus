package vn.t3nexus.scheduler.domain.scheduled_job_instance;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public final class ScheduledJobInstanceException {

    private ScheduledJobInstanceException() {}

    public static DomainException notFound() {
        return new DomainException(ScheduledJobInstanceErrorCode.SCHEDULED_JOB_INSTANCE_NOT_FOUND);
    }
}
