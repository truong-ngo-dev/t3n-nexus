package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeScope;

import java.util.List;

public interface AttributeTemplateJpaRepository extends JpaRepository<AttributeTemplateJpaEntity, String> {

    boolean existsByName(String name);

    List<AttributeTemplateJpaEntity> findAllByScope(AttributeScope scope);
}
