package vn.t3nexus.scheduler.presentation.scheduled_job.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ScheduledJobResponse(
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
    public record PagedResponse(List<ScheduledJobResponse> items, long total, int page, int size) {}
}
