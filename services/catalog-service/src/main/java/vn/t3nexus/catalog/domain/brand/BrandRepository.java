package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface BrandRepository extends Repository<Brand, BrandId> {

    /** Tên đã có (so theo khóa tên). Kiểm sớm để trả lỗi rõ; ràng buộc duy nhất của cơ sở dữ liệu là chốt cuối (ADR-0011). */
    boolean existsByNameKey(String nameKey);

    /** Như {@link #existsByNameKey} nhưng bỏ qua chính thương hiệu đang đổi tên. */
    boolean existsByNameKeyExcludingId(String nameKey, BrandId excludingId);

    /**
     * Danh sách theo từ khóa trên khóa tên (không phân biệt hoa thường, dấu); {@code status == null}: mọi trạng thái
     * (danh sách quản trị). Từ khóa là khóa tên đã chuẩn hóa hoặc null = không lọc. {@code page} bắt đầu từ 0.
     */
    List<Brand> search(String keywordKey, BrandStatus status, int page, int size);

    long count(String keywordKey, BrandStatus status);

    /** Thương hiệu không bao giờ bị xóa, chỉ ngừng dùng (INV-CAT-21). */
    @Override
    default void delete(BrandId id) {
        throw new UnsupportedOperationException("Brand is never deleted: deactivate instead");
    }
}
