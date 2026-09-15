package vn.t3nexus.scheduler.domain.scheduled_job;

import java.time.Instant;

/**
 * Projection nhẹ trả về từ {@link ScheduledJobRepository#findDueBefore} — chỉ {@code id} + thời điểm đến
 * hạn, đủ cho {@code IndexReconciler} gọi {@link DueItemFinder#index(ScheduledJobId, Instant)} mà không
 * phải load cả aggregate {@link ScheduledJob} (map {@code Schedule} VO, payload...).
 *
 * @param id    id job đang RUNNING
 * @param dueAt {@code next_fire_at} tại thời điểm quét — dùng làm score khi ghi vào index
 */
public record DueJobRef(ScheduledJobId id, Instant dueAt) {}
