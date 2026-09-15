package vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job;

import tools.jackson.core.type.TypeReference;
import vn.t3nexus.lib.utils.JsonUtils;
import vn.t3nexus.lib.utils.reflect.ReflectionUtils;
import vn.t3nexus.scheduler.domain.schedule.OneOffSchedule;
import vn.t3nexus.scheduler.domain.schedule.RecurringSchedule;
import vn.t3nexus.scheduler.domain.schedule.Schedule;
import vn.t3nexus.scheduler.domain.scheduled_job.MisfireInstruction;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobStatus;

import java.lang.reflect.Field;
import java.time.ZoneId;
import java.util.Map;

public final class ScheduledJobMapper {

    private ScheduledJobMapper() {}

    private static final Field VERSION_FIELD = ReflectionUtils.findField(ScheduledJob.class, "version");

    public static ScheduledJob toDomain(ScheduledJobJpaEntity e) {
        ScheduledJob job = ScheduledJob.reconstitute(
                ScheduledJobId.of(e.getId()),
                e.getJobName(),
                e.getTaskType(),
                toSchedule(e),
                JsonUtils.fromJson(e.getPayloadJson(), new TypeReference<Map<String, Object>>() {}),
                ScheduledJobStatus.valueOf(e.getStatus()),
                e.getNextFireAt(),
                MisfireInstruction.valueOf(e.getMisfireInstruction()),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
        ReflectionUtils.setField(VERSION_FIELD, job, e.getVersion());
        return job;
    }

    public static ScheduledJobJpaEntity toJpaEntity(ScheduledJob job) {
        ScheduledJobJpaEntity e = new ScheduledJobJpaEntity();
        e.setId(job.getId().getValue());
        e.setJobName(job.getJobName());
        e.setTaskType(job.getTaskType());
        fillScheduleColumns(e, job.getSchedule());
        e.setPayloadJson(JsonUtils.toJson(job.getPayload()));
        e.setStatus(job.getStatus().name());
        e.setNextFireAt(job.getNextFireAt());
        e.setMisfireInstruction(job.getMisfireInstruction().name());
        e.setVersion((Long) ReflectionUtils.getField(VERSION_FIELD, job));
        e.setCreatedAt(job.getCreatedAt());
        e.setUpdatedAt(job.getUpdatedAt());
        return e;
    }

    private static Schedule toSchedule(ScheduledJobJpaEntity e) {
        return switch (e.getScheduleType()) {
            case "ONE_OFF" -> new OneOffSchedule(e.getDueAt());
            case "RECURRING" -> new RecurringSchedule(e.getCronExpression(), ZoneId.of(e.getTimezone()));
            default -> throw new IllegalStateException("Unknown schedule_type: " + e.getScheduleType());
        };
    }

    private static void fillScheduleColumns(ScheduledJobJpaEntity e, Schedule schedule) {
        switch (schedule) {
            case OneOffSchedule oneOff -> {
                e.setScheduleType("ONE_OFF");
                e.setDueAt(oneOff.dueAt());
                e.setCronExpression(null);
                e.setTimezone(null);
            }
            case RecurringSchedule recurring -> {
                e.setScheduleType("RECURRING");
                e.setCronExpression(recurring.cron());
                e.setTimezone(recurring.zone().getId());
                e.setDueAt(null);
            }
        }
    }
}
