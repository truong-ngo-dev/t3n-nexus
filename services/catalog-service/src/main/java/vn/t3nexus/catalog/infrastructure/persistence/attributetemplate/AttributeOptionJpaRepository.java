package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface AttributeOptionJpaRepository extends JpaRepository<AttributeOptionJpaEntity, String> {

    List<AttributeOptionJpaEntity> findByTemplateId(String templateId);

    /** Batch — dùng bởi {@code findAllByIds}, tránh phải {@code findAll()} (load option của MỌI template). */
    List<AttributeOptionJpaEntity> findByTemplateIdIn(Collection<String> templateIds);

    /**
     * Bulk update trực tiếp trên cột {@code usage_count} — dùng bởi {@code AddVariant}, KHÔNG đi qua
     * {@code AttributeTemplate.save()} (vốn xoá-hết-rồi-insert-lại TOÀN BỘ option của template, quá đắt
     * cho việc chỉ bump 1 counter mỗi lần tạo Variant). Xem javadoc
     * {@code AttributeTemplateRepository#incrementOptionUsage}.
     */
    @Modifying
    @Query("UPDATE AttributeOptionJpaEntity o SET o.usageCount = o.usageCount + 1 WHERE o.id IN :ids")
    void incrementUsageCount(Collection<String> ids);

    @Transactional
    void deleteByTemplateId(String templateId);
}
