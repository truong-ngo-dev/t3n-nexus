package vn.t3nexus.scheduler.presentation.scheduled_job.model;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

/**
 * {@code scheduleType} chỉ validate {@code @NotBlank} ở đây — giá trị hợp lệ (ONE_OFF/RECURRING) và tổ
 * hợp field bắt buộc theo từng loại (dueAt vs cronExpression+timezone) do domain tự validate qua
 * {@code ScheduleRequestMapper}/{@code Schedule} VO (throw domain exception → 404/400 đúng qua
 * {@code GlobalExceptionHandler}), không lặp lại bằng Bean Validation ở đây.
 */
public record CreateScheduledJobRequest(
        @NotBlank String jobName,
        @NotBlank String taskType,
        @NotBlank String scheduleType,
        Instant dueAt,
        String cronExpression,
        String timezone,
        Map<String, Object> payload,
        String misfireInstruction
) {}
