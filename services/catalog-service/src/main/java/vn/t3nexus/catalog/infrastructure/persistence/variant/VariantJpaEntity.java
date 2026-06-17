package vn.t3nexus.catalog.infrastructure.persistence.variant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.catalog.domain.variant.VariantStatus;

import java.time.Instant;

@Entity
@Table(name = "variant")
@Getter
@Setter
@NoArgsConstructor
public class VariantJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    @Column(name = "combination_hash", nullable = false, updatable = false, length = 64)
    private String combinationHash;

    @Column(name = "sku_code")
    private String skuCode;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VariantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
