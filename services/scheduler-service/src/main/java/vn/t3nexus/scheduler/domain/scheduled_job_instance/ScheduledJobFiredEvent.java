package vn.t3nexus.scheduler.domain.scheduled_job_instance;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Raise bởi {@link ScheduledJobInstance#dispatch} — KHÔNG phải {@code ScheduledJob.fire()}. Nội dung
 * event (taskType/payload/instanceId) đều là field của chính {@code ScheduledJobInstance}, nên đúng
 * convention "Domain Event đặt trong package của aggregate phát ra event" thì nó thuộc về đây, không
 * phải {@code scheduled_job/}. {@code aggregateId}/{@code aggregateType} = instanceId/"ScheduledJobInstance"
 * — khớp đúng aggregate thật sự phát ra event, không phải {@code ScheduledJob}.
 */
public class ScheduledJobFiredEvent extends AbstractDomainEvent {

    private final String scheduledJobId;
    private final String taskType;
    private final Map<String, Object> payload;

    /**
     * @param instanceId    id của chính {@code ScheduledJobInstance} này — dùng làm aggregateId
     * @param scheduledJobId ref tới ScheduledJob đã tạo ra lần fire này — chỉ mang theo trong payload,
     *                      KHÔNG dùng làm aggregateId (khác trước đây)
     */
    public ScheduledJobFiredEvent(String instanceId, String scheduledJobId, String taskType, Map<String, Object> payload) {
        super(UUID.randomUUID().toString(), Instant.now(), instanceId, "ScheduledJobInstance");
        this.scheduledJobId = scheduledJobId;
        this.taskType = taskType;
        this.payload = payload;
    }

    @Override
    public String getRoutingKey() { return "scheduler.job.fired"; }

    @Override
    public Object getPayload() {
        return new Payload(scheduledJobId, getAggregateId(), taskType, payload);
    }

    public String scheduledJobId() { return scheduledJobId; }
    public String instanceId() { return getAggregateId(); }
    public String taskType() { return taskType; }
    public Map<String, Object> payload() { return payload; }

    public record Payload(String scheduledJobId, String instanceId, String taskType, Map<String, Object> payload) {}
}
