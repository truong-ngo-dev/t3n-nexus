package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Collection;
import java.util.List;

public interface AttributeTemplateRepository extends Repository<AttributeTemplate, AttributeTemplateId> {

    /** Tên đã có (so theo khóa tên). Kiểm sớm để trả lỗi rõ; ràng buộc duy nhất của cơ sở dữ liệu là chốt cuối (ADR-0011). */
    boolean existsByName(String name);

    /** Nạp theo lô các mẫu, một lần truy vấn cho cả danh sách (dùng khi gán thuộc tính cho danh mục). */
    List<AttributeTemplate> findAllByIds(Collection<AttributeTemplateId> ids);

    /**
     * Danh sách quản trị: mọi trạng thái, lọc theo từ khóa ({@code name}/{@code displayName}, không phân biệt hoa/thường),
     * {@code inputType}, {@code status}; tham số null = không lọc. {@code page} bắt đầu từ 0.
     */
    List<AttributeTemplate> search(String keyword, InputType inputType, AttributeTemplateStatus status,
                                   int page, int size);

    long count(String keyword, InputType inputType, AttributeTemplateStatus status);

    /** Mẫu không bao giờ bị xóa, chỉ ngừng dùng (INV-CAT-12). */
    @Override
    default void delete(AttributeTemplateId id) {
        throw new UnsupportedOperationException("AttributeTemplate is never deleted: deactivate instead");
    }
}
