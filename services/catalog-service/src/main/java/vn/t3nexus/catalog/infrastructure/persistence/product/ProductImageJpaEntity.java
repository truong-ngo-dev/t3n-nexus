package vn.t3nexus.catalog.infrastructure.persistence.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Dòng bảng {@code product_image} (V3). Cha-con với {@code product}; ghi lại cả danh sách khi thứ tự hoặc tập ảnh đổi. */
@Entity
@Table(name = "product_image")
@Getter
@NoArgsConstructor
public class ProductImageJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "object_key", nullable = false, updatable = false, length = 200)
    private String objectKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 20)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "position", nullable = false)
    private int position;

    public ProductImageJpaEntity(UUID id, UUID productId, String objectKey, String contentType, long sizeBytes,
                                 int position) {
        this.id = id;
        this.productId = productId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.position = position;
    }
}
