package vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledJobJpaRepository extends JpaRepository<ScheduledJobJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ScheduledJobJpaEntity e WHERE e.id = :id")
    Optional<ScheduledJobJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Query(value = "SELECT id AS id, next_fire_at AS nextFireAt FROM scheduled_job " +
                   "WHERE status = 'RUNNING' AND next_fire_at <= :asOf " +
                   "ORDER BY next_fire_at LIMIT :limit", nativeQuery = true)
    @SuppressWarnings("all")
    List<DueRefRow> findDueBefore(@Param("asOf") Instant asOf, @Param("limit") int limit);

    /** Interface projection cho {@link #findDueBefore} — alias cột khớp tên getter. */
    interface DueRefRow {
        String getId();
        Instant getNextFireAt();
    }

    /**
     * Backing query cho {@code ScheduledJobQueryAdapter} (impl của
     * {@code domain.scheduled_job.ScheduledJobQueryPort}) — dùng chung Spring Data repository này với
     * phần aggregate persistence, chỉ khác domain port gọi vào (tách port, không tách bảng/entity/JPA
     * repository — cùng 1 table thì không cần 2 lớp Spring Data riêng). {@code Pageable} tự thêm
     * {@code ORDER BY} — không khai báo tay trong JPQL.
     *
     * <p>{@code jobName} lọc kiểu <b>substring, case-insensitive</b> ({@code LOWER()+LIKE}, JPQL thuần —
     * không dùng {@code ILIKE} của Postgres để giữ portable) — khác {@code status}/{@code taskType}
     * (exact match). Xem javadoc {@code ScheduledJobQueryFilter}.
     */
    @Query("SELECT e FROM ScheduledJobJpaEntity e " +
           "WHERE (:jobName IS NULL OR LOWER(e.jobName) LIKE LOWER(CONCAT('%', :jobName, '%'))) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:taskType IS NULL OR e.taskType = :taskType)")
    Page<ScheduledJobJpaEntity> search(@Param("jobName") String jobName, @Param("status") String status,
                                       @Param("taskType") String taskType, Pageable pageable);

    @Query("SELECT COUNT(e) FROM ScheduledJobJpaEntity e " +
           "WHERE (:jobName IS NULL OR LOWER(e.jobName) LIKE LOWER(CONCAT('%', :jobName, '%'))) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:taskType IS NULL OR e.taskType = :taskType)")
    long countSearch(@Param("jobName") String jobName, @Param("status") String status,
                     @Param("taskType") String taskType);
}
