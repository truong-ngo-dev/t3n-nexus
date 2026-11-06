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

/** Một tùy chọn đã chọn của thuộc tính kiểu chọn (lưu mã tùy chọn, không lưu chữ). */
@Entity
@Table(name = "product_attribute_option")
@IdClass(ProductAttributeOptionKey.class)
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeOptionJpaEntity {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Id
    @Column(name = "option_id", nullable = false, updatable = false)
    private UUID optionId;
}
