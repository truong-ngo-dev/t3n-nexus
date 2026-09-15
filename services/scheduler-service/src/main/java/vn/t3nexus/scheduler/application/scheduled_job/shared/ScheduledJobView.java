package vn.t3nexus.scheduler.application.scheduled_job.shared;

import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;

import java.time.Instant;
import java.util.Map;

/**
 * Hình chiếu phẳng của {@link ScheduledJob} — dùng chung cho {@code GetScheduledJob} và
 * {@code ListScheduledJobs} (Query Handler), tránh lặp lại mapping ở 2 nơi. Presentation layer
 * ({@code ScheduledJobController}) map tiếp record này sang {@code ScheduledJobResponse} riêng của nó —
 * không trả thẳng type application ra REST.
 */
public record ScheduledJobView(
        String id,
        String jobName,
        String taskType,
        String scheduleType,
        Instant dueAt,
        String cronExpression,
        String timezone,
        Map<String, Object> payload,
        String status,
        Instant nextFireAt,
        String misfireInstruction,
        Instant createdAt,
        Instant updatedAt
) {
    public static ScheduledJobView from(ScheduledJob job) {
        ScheduleView schedule = ScheduleView.from(job.getSchedule());
        return new ScheduledJobView(
                job.getId().getValue(),
                job.getJobName(),
                job.getTaskType(),
                schedule.scheduleType(),
                schedule.dueAt(),
                schedule.cronExpression(),
                schedule.timezone(),
                job.getPayload(),
                job.getStatus().name(),
                job.getNextFireAt(),
                job.getMisfireInstruction().name(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
