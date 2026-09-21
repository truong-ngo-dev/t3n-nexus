package vn.t3nexus.customer.infrastructure.persistence.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface CustomerProfileJpaRepository extends JpaRepository<CustomerProfileJpaEntity, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO customer_profiles (id, created_at, updated_at)
            VALUES (:id, :createdAt, :updatedAt)
            ON CONFLICT (id) DO NOTHING
            """)
    void insertIgnoreConflict(
            @Param("id") String id,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt
    );
}
