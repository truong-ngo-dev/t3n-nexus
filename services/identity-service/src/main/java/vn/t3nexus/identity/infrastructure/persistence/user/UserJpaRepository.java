package vn.t3nexus.identity.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, String> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO users (id, email, phone_number, full_name, hashed_password, role, status, locked_at, created_at, updated_at)
            VALUES (:id, :email, :phoneNumber, :fullName, :hashedPassword, :role, :status, :lockedAt, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET
                phone_number    = EXCLUDED.phone_number,
                full_name       = EXCLUDED.full_name,
                hashed_password = EXCLUDED.hashed_password,
                status          = EXCLUDED.status,
                locked_at       = EXCLUDED.locked_at,
                updated_at      = EXCLUDED.updated_at
            """)
    void upsert(
            @Param("id")             String id,
            @Param("email")          String email,
            @Param("phoneNumber")    String phoneNumber,
            @Param("fullName")       String fullName,
            @Param("hashedPassword") String hashedPassword,
            @Param("role")           String role,
            @Param("status")         String status,
            @Param("lockedAt")       Instant lockedAt,
            @Param("createdAt")      Instant createdAt,
            @Param("updatedAt")      Instant updatedAt
    );
}
