package vn.t3nexus.customer.infrastructure.persistence.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface CustomerProfileJpaRepository extends JpaRepository<CustomerProfileJpaEntity, String> {

    Optional<CustomerProfileJpaEntity> findByUserId(String userId);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO customer_profiles (id, user_id, created_at, updated_at)
            VALUES (:id, :userId, :createdAt, :updatedAt)
            ON CONFLICT (user_id) DO NOTHING
            """)
    void insertIgnoreConflict(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt
    );
}
