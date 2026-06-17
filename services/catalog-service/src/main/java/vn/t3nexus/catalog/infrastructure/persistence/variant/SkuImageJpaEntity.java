package vn.t3nexus.catalog.infrastructure.persistence.variant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sku_image")
@Getter
@Setter
@NoArgsConstructor
public class SkuImageJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "variant_id", nullable = false)
    private String variantId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
