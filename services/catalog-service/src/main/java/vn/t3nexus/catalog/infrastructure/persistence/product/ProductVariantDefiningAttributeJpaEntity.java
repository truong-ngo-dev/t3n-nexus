package vn.t3nexus.catalog.infrastructure.persistence.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_variant_defining_attribute")
@IdClass(ProductVariantDefiningAttributeKey.class)
@Getter
@Setter
@NoArgsConstructor
public class ProductVariantDefiningAttributeJpaEntity {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private String templateId;
}
