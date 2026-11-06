package vn.t3nexus.lib.upload;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface UploadSlotJpaRepository extends JpaRepository<UploadSlotEntity, UUID> {

    @Query("""
            SELECT COUNT(s) FROM UploadSlotEntity s
            WHERE s.purpose = :purpose AND s.accountId = :accountId AND s.targetId = :targetId AND s.expiresAt > :now
            """)
    long countActive(@Param("purpose") String purpose, @Param("accountId") UUID accountId,
                     @Param("targetId") UUID targetId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UploadSlotEntity s WHERE s.id = :id")
    int deleteSlot(@Param("id") UUID id);

    List<UploadSlotEntity> findByExpiresAtBeforeOrderByExpiresAtAsc(Instant before, Pageable pageable);
}
