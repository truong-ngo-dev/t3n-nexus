package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface AttributeTemplateRepository extends Repository<AttributeTemplate, AttributeTemplateId> {

    boolean existsByName(String name);

    List<AttributeTemplate> findAll();

    List<AttributeTemplate> findAllByScope(AttributeScope scope);
}
