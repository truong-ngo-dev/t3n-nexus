package vn.t3nexus.scheduler.domain.scheduled_job_instance;

import vn.t3nexus.lib.common.domain.service.Repository;

/**
 * Chỉ write-side (per-instanceId load/save/delete). Query theo scheduledJobId (lịch sử các lần chạy
 * của 1 job, dùng cho audit/UI) là read-side, bypass domain — đặt ở
 * {@code infrastructure/adapter/query/scheduled_job_instance/} khi cần, không thuộc interface này
 * (xem ddd-structure.md §adapter/query).
 */
public interface ScheduledJobInstanceRepository extends Repository<ScheduledJobInstance, ScheduledJobInstanceId> {
}
