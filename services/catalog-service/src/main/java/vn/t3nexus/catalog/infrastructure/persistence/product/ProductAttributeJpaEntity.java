package vn.t3nexus.catalog.infrastructure.persistence.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Một thuộc tính của sản phẩm: cờ trục và giá trị rời (kiểu chọn để rỗng, dùng bảng tùy chọn). */
@Entity
@Table(name = "product_attribute")
@IdClass(ProductAttributeKey.class)
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeJpaEntity {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "axis", nullable = false)
    private boolean axis;

    @Column(name = "value")
    private String value;
}
