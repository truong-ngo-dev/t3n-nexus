package vn.t3nexus.scheduler.domain.scheduled_job_instance;

public enum ScheduledJobInstanceStatus {
    /** Signal đã publish (T_emit) — chưa nhận callback từ consumer. */
    DISPATCHED,
    /** Terminal — consumer báo hoàn thành thành công. */
    SUCCEEDED,
    /** Terminal — consumer báo lỗi (xem failureReason). */
    FAILED
}
