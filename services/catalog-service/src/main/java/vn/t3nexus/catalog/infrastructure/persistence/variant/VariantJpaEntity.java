package vn.t3nexus.catalog.infrastructure.persistence.variant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.catalog.domain.variant.VariantStatus;

import java.time.Instant;
import java.util.UUID;

/** Dòng biến thể. Không có {@code version} riêng: mọi lệnh biến thể tuần tự qua dòng sản phẩm (ADR-0012). */
@Entity
@Table(name = "variant")
@Getter
@Setter
@NoArgsConstructor
public class VariantJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "combination_key", nullable = false, updatable = false)
    private String combinationKey;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "sku")
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VariantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
