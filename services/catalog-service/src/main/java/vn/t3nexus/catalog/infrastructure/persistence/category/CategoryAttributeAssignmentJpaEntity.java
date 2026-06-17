package vn.t3nexus.catalog.infrastructure.persistence.category;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "category_attribute_assignment")
@IdClass(CategoryAssignmentKey.class)
@Getter
@Setter
@NoArgsConstructor
public class CategoryAttributeAssignmentJpaEntity {

    @Id
    @Column(name = "category_id", nullable = false, updatable = false)
    private String categoryId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private String templateId;

    @Column(name = "is_variant_defining", nullable = false)
    private boolean variantDefining;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "is_filterable", nullable = false)
    private boolean filterable;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
