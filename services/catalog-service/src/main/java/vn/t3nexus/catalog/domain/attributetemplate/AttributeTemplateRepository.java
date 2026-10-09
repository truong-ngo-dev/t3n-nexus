package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Collection;
import java.util.List;

public interface AttributeTemplateRepository extends Repository<AttributeTemplate, AttributeTemplateId> {

    boolean existsByName(String name);

    /**
     * Batch load theo id — dùng bởi {@code AttributeTemplateDomainService#validateProductAttributes}
     * để lấy đúng 1 round-trip cho toàn bộ attribute submit cùng lúc (bất kể submit bao nhiêu attribute),
     * tránh N+1 khi cần {@code inputType}/{@code options} đầy đủ của từng template để validate.
     */
    List<AttributeTemplate> findAllByIds(Collection<AttributeTemplateId> ids);

    /**
     * Danh sách quản trị: mọi trạng thái, lọc theo từ khoá ({@code name}/{@code displayName}, không phân biệt hoa/thường),
     * {@code inputType}, {@code status}; tham số null = không lọc. {@code page} bắt đầu từ 0.
     */
    List<AttributeTemplate> search(String keyword, InputType inputType, AttributeTemplateStatus status,
                                   int page, int size);

    long count(String keyword, InputType inputType, AttributeTemplateStatus status);

    /** Thuộc tính không bao giờ bị xoá — chỉ tắt/bật (sản phẩm cũ còn tham chiếu). */
    @Override
    default void delete(AttributeTemplateId id) {
        throw new UnsupportedOperationException("AttributeTemplate is never deleted — deactivate instead");
    }
}
