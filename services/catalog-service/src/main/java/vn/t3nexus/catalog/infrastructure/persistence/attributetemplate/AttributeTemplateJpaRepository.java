package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeTemplateJpaRepository extends JpaRepository<AttributeTemplateJpaEntity, String> {

    boolean existsByName(String name);
}
