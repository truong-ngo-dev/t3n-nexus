package vn.t3nexus.catalog.infrastructure.persistence.sellerstate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface SellerStateJpaRepository extends JpaRepository<SellerStateJpaEntity, UUID> {

    /**
     * Ghi hoặc ghi đè nếu {@code version} mới hơn bản đã lưu, trong một câu lệnh nên nhận lặp, sai thứ tự và hai
     * luồng cùng lúc đều an toàn. Trả 1 nếu có ghi, 0 nếu bỏ qua.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO seller_state (account_id, status, version, updated_at)
            VALUES (:accountId, :status, :version, :now)
            ON CONFLICT (account_id) DO UPDATE
               SET status = EXCLUDED.status, version = EXCLUDED.version, updated_at = EXCLUDED.updated_at
             WHERE seller_state.version < EXCLUDED.version
            """, nativeQuery = true)
    int applyDecision(@Param("accountId") UUID accountId, @Param("status") String status,
                      @Param("version") long version, @Param("now") Instant now);
}
