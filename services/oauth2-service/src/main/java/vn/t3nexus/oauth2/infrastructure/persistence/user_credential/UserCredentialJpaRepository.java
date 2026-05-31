package vn.t3nexus.oauth2.infrastructure.persistence.user_credential;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserCredentialJpaRepository extends JpaRepository<UserCredentialJpaEntity, String> {

    Optional<UserCredentialJpaEntity> findByEmail(String email);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO user_credentials (id, email, password_hash, role, registration_method, status, created_at, updated_at)
            VALUES (:id, :email, :passwordHash, :role, :registrationMethod, :status, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET
                password_hash       = EXCLUDED.password_hash,
                status              = EXCLUDED.status,
                updated_at          = EXCLUDED.updated_at
            """)
    void upsert(
            @Param("id")                 String  id,
            @Param("email")              String  email,
            @Param("passwordHash")       String  passwordHash,
            @Param("role")               String  role,
            @Param("registrationMethod") String  registrationMethod,
            @Param("status")             String  status,
            @Param("createdAt")          Instant createdAt,
            @Param("updatedAt")          Instant updatedAt
    );
}
