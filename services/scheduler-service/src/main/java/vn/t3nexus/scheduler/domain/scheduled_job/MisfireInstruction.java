package vn.t3nexus.scheduler.domain.scheduled_job;

/**
 * Hành vi khi Poll phát hiện 1 job trễ quá {@code misfireThreshold} (config cấp scheduler, application
 * property {@code scheduler.misfire-threshold-seconds} — KHÔNG phải per-job, xem service.md Business
 * Rules). Chỉ thật sự có hiệu lực với {@link vn.t3nexus.scheduler.domain.schedule.RecurringSchedule} —
 * job one-off overdue luôn hành xử như FIRE_NOW bất kể giá trị này (xem
 * {@link ScheduledJob#skipMisfire(java.time.Instant, vn.t3nexus.scheduler.domain.schedule.CronCalculator)}).
 */
public enum MisfireInstruction {
    /** Fire bù ngay 1 lần, các lần sau tính bình thường — default. */
    FIRE_NOW,
    /** Không fire bù — tính next-fire-time bình thường, đợi thẳng lần kế tiếp, không phát event. */
    DO_NOTHING
}
