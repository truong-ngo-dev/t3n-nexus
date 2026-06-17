package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AttributeOptionJpaRepository extends JpaRepository<AttributeOptionJpaEntity, String> {

    List<AttributeOptionJpaEntity> findByTemplateId(String templateId);

    @Transactional
    void deleteByTemplateId(String templateId);
}
