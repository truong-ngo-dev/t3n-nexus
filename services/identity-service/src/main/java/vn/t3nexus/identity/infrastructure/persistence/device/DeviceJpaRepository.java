package vn.t3nexus.identity.infrastructure.persistence.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceJpaRepository extends JpaRepository<DeviceJpaEntity, String> {

    List<DeviceJpaEntity> findByUserId(String userId);

    List<DeviceJpaEntity> findByUserIdAndStatus(String userId, String status);

    Optional<DeviceJpaEntity> findByUserIdAndCompositeHash(String userId, String compositeHash);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO devices (
                id, user_id, device_hash, user_agent, accept_language, composite_hash,
                device_name, device_type, trusted, status, registered_at, last_seen_at, last_ip_address
            ) VALUES (
                :id, :userId, :deviceHash, :userAgent, :acceptLanguage, :compositeHash,
                :deviceName, :deviceType, :trusted, :status, :registeredAt, :lastSeenAt, :lastIpAddress
            )
            ON CONFLICT (id) DO UPDATE SET
                device_name     = EXCLUDED.device_name,
                device_type     = EXCLUDED.device_type,
                trusted         = EXCLUDED.trusted,
                status          = EXCLUDED.status,
                last_seen_at    = EXCLUDED.last_seen_at,
                last_ip_address = EXCLUDED.last_ip_address
            """)
    void upsert(
            @Param("id")             String id,
            @Param("userId")         String userId,
            @Param("deviceHash")     String deviceHash,
            @Param("userAgent")      String userAgent,
            @Param("acceptLanguage") String acceptLanguage,
            @Param("compositeHash")  String compositeHash,
            @Param("deviceName")     String deviceName,
            @Param("deviceType")     String deviceType,
            @Param("trusted")        boolean trusted,
            @Param("status")         String status,
            @Param("registeredAt")   Instant registeredAt,
            @Param("lastSeenAt")     Instant lastSeenAt,
            @Param("lastIpAddress")  String lastIpAddress
    );
}
