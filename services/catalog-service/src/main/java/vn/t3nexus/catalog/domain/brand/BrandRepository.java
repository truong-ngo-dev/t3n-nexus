package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface BrandRepository extends Repository<Brand, BrandId> {

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    boolean existsByNameExcludingId(String name, BrandId excludingId);

    /** {@code status == null}: mọi trạng thái (dùng cho danh sách quản trị). */
    List<Brand> search(String keyword, BrandStatus status, int page, int size);

    long count(String keyword, BrandStatus status);

    /** Thương hiệu không bao giờ bị xoá — chỉ tắt/bật (sản phẩm cũ còn tham chiếu). */
    @Override
    default void delete(BrandId id) {
        throw new UnsupportedOperationException("Brand is never deleted — deactivate instead");
    }
}
