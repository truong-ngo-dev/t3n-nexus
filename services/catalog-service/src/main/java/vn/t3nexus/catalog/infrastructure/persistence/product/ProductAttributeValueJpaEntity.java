package vn.t3nexus.catalog.infrastructure.persistence.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_attribute_value")
@IdClass(ProductAttributeValueKey.class)
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeValueJpaEntity {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private String templateId;

    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    private String value;
}
