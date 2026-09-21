package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface CategoryRepository extends Repository<Category, CategoryId> {

    boolean existsBySlug(String slug);

    boolean existsByParentId(CategoryId parentId);

    boolean hasProductReference(CategoryId categoryId);

    List<Category> findAll();

    /**
     * Dùng bởi {@code AttributeTemplateDomainService} để chặn deactivate 1 template đang {@code required}
     * ở bất kỳ category nào — tránh deadlock (category đòi hỏi bắt buộc 1 attribute không còn dùng được).
     */
    boolean existsRequiredAssignmentByTemplateId(AttributeTemplateId templateId);
}
