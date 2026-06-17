package vn.t3nexus.catalog.infrastructure.persistence.variant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "variant_combination_item")
@IdClass(VariantCombinationItemKey.class)
@Getter
@Setter
@NoArgsConstructor
public class VariantCombinationItemJpaEntity {

    @Id
    @Column(name = "variant_id", nullable = false, updatable = false)
    private String variantId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private String templateId;

    @Id
    @Column(name = "option_id", nullable = false, updatable = false)
    private String optionId;
}
