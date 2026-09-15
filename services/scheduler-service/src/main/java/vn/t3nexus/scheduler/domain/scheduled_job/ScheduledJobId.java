package vn.t3nexus.scheduler.domain.scheduled_job;

import vn.t3nexus.lib.common.domain.model.AbstractId;

/**
 * Không có {@code generate()} tĩnh — ULID sinh qua {@code ULIDGenerator} injected ở Application
 * Handler (pre-compute, không phải Double Dispatch — {@code generate()} không đọc field nào của
 * aggregate, xem ddd-structure.md §Double Dispatch, ví dụ ID generation), khớp đúng convention thật
 * của {@code order-service}/{@code identity-service}/{@code customer-service}.
 */
public final class ScheduledJobId extends AbstractId<String> {

    private ScheduledJobId(String value) {
        super(value);
    }

    public static ScheduledJobId of(String value) {
        return new ScheduledJobId(value);
    }
}
