package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AttributeOptionJpaRepository extends JpaRepository<AttributeOptionJpaEntity, String> {

    List<AttributeOptionJpaEntity> findByTemplateId(String templateId);

    /** Batch — dùng bởi {@code findAllByIds}/{@code search}, chỉ load option của đúng các template cần. */
    List<AttributeOptionJpaEntity> findByTemplateIdIn(Collection<String> templateIds);
}
