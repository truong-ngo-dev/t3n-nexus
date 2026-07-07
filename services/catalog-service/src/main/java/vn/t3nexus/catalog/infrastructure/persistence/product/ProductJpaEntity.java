package vn.t3nexus.catalog.infrastructure.persistence.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.catalog.domain.product.ProductStatus;

import java.time.Instant;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class ProductJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private String sellerId;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "brand_id", nullable = false)
    private String brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    @Column(name = "warranty_type")
    private String warrantyType;

    @Column(name = "warranty_coverage")
    private String warrantyCoverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "admin_blocked", nullable = false)
    private boolean adminBlocked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
