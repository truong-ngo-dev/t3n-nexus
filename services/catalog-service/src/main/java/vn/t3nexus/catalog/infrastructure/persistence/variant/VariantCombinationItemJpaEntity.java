package vn.t3nexus.catalog.infrastructure.persistence.variant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Một cặp của tổ hợp: tùy chọn được chọn cho một trục. Bất biến sau khi biến thể được tạo (INV-CAT-41). */
@Entity
@Table(name = "variant_combination_item")
@IdClass(VariantCombinationItemKey.class)
@Getter
@Setter
@NoArgsConstructor
public class VariantCombinationItemJpaEntity {

    @Id
    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "option_id", nullable = false, updatable = false)
    private UUID optionId;
}
