package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Collection;
import java.util.List;

public interface AttributeTemplateRepository extends Repository<AttributeTemplate, AttributeTemplateId> {

    boolean existsByName(String name);

    List<AttributeTemplate> findAll();

    /**
     * Batch load theo id — dùng bởi {@code AttributeTemplateDomainService#validateProductAttributes}
     * để lấy đúng 1 round-trip cho toàn bộ attribute submit cùng lúc (bất kể submit bao nhiêu attribute),
     * tránh N+1 khi cần {@code inputType}/{@code options} đầy đủ của từng template để validate.
     */
    List<AttributeTemplate> findAllByIds(Collection<AttributeTemplateId> ids);

    /**
     * Bump {@code usageCount} của các option — dùng bởi {@code AddVariant} mỗi khi 1 optionId được đưa
     * vào {@code combination}. Cố tình KHÔNG đi qua load-modify-{@link #save}: {@code save} xoá-hết-rồi-
     * insert-lại TOÀN BỘ option của template, quá đắt cho việc chỉ bump 1 counter mỗi lần tạo Variant
     * (write path rất thường xuyên) — implementation phải là bulk update trực tiếp trên đúng các option.
     */
    void incrementOptionUsage(Collection<AttributeOptionId> optionIds);
}
