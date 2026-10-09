package vn.t3nexus.catalog.infrastructure.persistence.category;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // JSON — shape theo AttributeConstraints/Discovery (domain), khác nhau theo inputType của template
    // (AGG-CAT-01/AGG-CAT-03). Không tách cột riêng: mỗi kiểu nhập dùng một tập field khác nhau, tách cột sẽ
    // toàn NULL chéo nhau.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraints", nullable = false, columnDefinition = "jsonb")
    private String constraintsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discovery", nullable = false, columnDefinition = "jsonb")
    private String discoveryJson;
}
