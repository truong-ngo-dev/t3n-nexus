package vn.t3nexus.scheduler.presentation.scheduled_job.model;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

/** Không có {@code taskType} — domain (`ScheduledJob.edit()`) không cho sửa field này, xem service.md. */
public record EditScheduledJobRequest(
        @NotBlank String jobName,
        @NotBlank String scheduleType,
        Instant dueAt,
        String cronExpression,
        String timezone,
        Map<String, Object> payload,
        String misfireInstruction
) {}
