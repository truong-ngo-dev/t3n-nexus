package vn.t3nexus.inventory.infrastructure.persistence.limitedoffer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferStatus;

import java.time.Instant;

@Entity
@Table(name = "limited_offer")
@Getter
@Setter
@NoArgsConstructor
public class LimitedOfferJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    @Column(name = "sku_id", nullable = false, updatable = false, unique = true, length = 26)
    private String skuId;

    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LimitedOfferStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
