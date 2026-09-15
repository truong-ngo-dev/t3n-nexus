package vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job_instance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Không có {@code @Version} — không có 2 writer đồng thời trên cùng 1 row, xem data.md §Locking. */
@Entity
@Table(name = "scheduled_job_instance")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledJobInstanceJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    @Column(name = "scheduled_job_id", nullable = false, updatable = false, length = 26)
    private String scheduledJobId;

    @Column(name = "task_type", nullable = false, updatable = false, length = 100)
    private String taskType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "fired_at", nullable = false, updatable = false)
    private Instant firedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason")
    private String failureReason;
}
