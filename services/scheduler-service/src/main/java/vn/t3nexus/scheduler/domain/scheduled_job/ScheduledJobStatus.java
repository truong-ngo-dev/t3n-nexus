package vn.t3nexus.scheduler.domain.scheduled_job;

public enum ScheduledJobStatus {
    /** Chưa activate (mới tạo, hoặc vừa bị stop()/edit()) — nextFireAt luôn null. */
    PENDING,
    /** Đang được poll — nextFireAt luôn có giá trị. */
    RUNNING,
    /** Terminal cho one-off job đã fire xong — nhưng edit() vẫn mở lại được (→ PENDING). */
    COMPLETED
}
