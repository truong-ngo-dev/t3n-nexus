package vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job_instance;

import tools.jackson.core.type.TypeReference;
import vn.t3nexus.lib.utils.JsonUtils;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstance;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstanceId;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstanceStatus;

import java.util.Map;

/** Không cần reflection cho {@code version} — {@code scheduled_job_instance} không có cột `@Version`. */
public final class ScheduledJobInstanceMapper {

    private ScheduledJobInstanceMapper() {}

    public static ScheduledJobInstance toDomain(ScheduledJobInstanceJpaEntity e) {
        return ScheduledJobInstance.reconstitute(
                ScheduledJobInstanceId.of(e.getId()),
                ScheduledJobId.of(e.getScheduledJobId()),
                e.getTaskType(),
                JsonUtils.fromJson(e.getPayloadJson(), new TypeReference<Map<String, Object>>() {}),
                ScheduledJobInstanceStatus.valueOf(e.getStatus()),
                e.getFiredAt(),
                e.getCompletedAt(),
                e.getFailureReason()
        );
    }

    public static ScheduledJobInstanceJpaEntity toJpaEntity(ScheduledJobInstance instance) {
        ScheduledJobInstanceJpaEntity e = new ScheduledJobInstanceJpaEntity();
        e.setId(instance.getId().getValue());
        e.setScheduledJobId(instance.getScheduledJobId().getValue());
        e.setTaskType(instance.getTaskType());
        e.setPayloadJson(JsonUtils.toJson(instance.getPayload()));
        e.setStatus(instance.getStatus().name());
        e.setFiredAt(instance.getFiredAt());
        e.setCompletedAt(instance.getCompletedAt());
        e.setFailureReason(instance.getFailureReason());
        return e;
    }
}
