package vn.t3nexus.catalog.domain.product;

import java.util.Objects;

/**
 * Ảnh sản phẩm (giá trị có mã, không kế thừa AbstractEntity).
 */
public final class ProductImage {

    private final ProductImageId id;
    private final String objectKey;
    private final String contentType;
    private final long sizeBytes;
    private final int position;

    public ProductImage(ProductImageId id, String objectKey, String contentType, long sizeBytes, int position) {
        this.id = id;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.position = position;
    }

    public static ProductImage of(ProductImageId id, String objectKey, String contentType, long sizeBytes, int position) {
        return new ProductImage(id, objectKey, contentType, sizeBytes, position);
    }

    public ProductImage withPosition(int newPosition) {
        return new ProductImage(this.id, this.objectKey, this.contentType, this.sizeBytes, newPosition);
    }

    public ProductImageId getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductImage that = (ProductImage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
