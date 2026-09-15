package vn.t3nexus.scheduler.domain.scheduled_job_instance;

import vn.t3nexus.lib.common.domain.model.AbstractId;

/**
 * Không có {@code generate()} tĩnh — cùng lý do {@code ScheduledJobId}: ULID sinh qua
 * {@code ULIDGenerator} pre-compute ở {@code ScheduledJobFireService}, không phải Double Dispatch.
 */
public final class ScheduledJobInstanceId extends AbstractId<String> {

    private ScheduledJobInstanceId(String value) {
        super(value);
    }

    public static ScheduledJobInstanceId of(String value) {
        return new ScheduledJobInstanceId(value);
    }
}
