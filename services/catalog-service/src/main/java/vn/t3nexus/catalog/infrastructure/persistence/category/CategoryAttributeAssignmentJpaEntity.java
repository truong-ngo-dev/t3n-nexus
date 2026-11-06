package vn.t3nexus.catalog.infrastructure.persistence.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "category_attribute_assignment")
@IdClass(CategoryAssignmentKey.class)
@Getter
@Setter
@NoArgsConstructor
public class CategoryAttributeAssignmentJpaEntity {

    @Id
    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // JSON theo hình của AttributeConstraints; mỗi kiểu nhập dùng một tập trường khác nhau nên không tách cột riêng.
    // Lưu trữ chỉ chuyển đổi, không diễn giải (miền đã kiểm hình trước khi lưu).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraints", nullable = false, columnDefinition = "jsonb")
    private String constraintsJson;
}
