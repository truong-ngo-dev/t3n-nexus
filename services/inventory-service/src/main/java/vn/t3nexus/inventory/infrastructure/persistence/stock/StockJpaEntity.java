package vn.t3nexus.inventory.infrastructure.persistence.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "stock")
@Getter
@Setter
@NoArgsConstructor
public class StockJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    @Column(name = "sku_id", nullable = false, updatable = false, unique = true, length = 26)
    private String skuId;

    @Column(name = "product_id", nullable = false, updatable = false, length = 26)
    private String productId;

    @Column(name = "seller_id", nullable = false, updatable = false, length = 26)
    private String sellerId;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "seller_active", nullable = false)
    private boolean sellerActive;

    @Column(name = "product_published", nullable = false)
    private boolean productPublished;

    @Column(name = "admin_blocked", nullable = false)
    private boolean adminBlocked;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
