package vn.t3nexus.catalog.infrastructure.adapter.repository.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeScope;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeOptionJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeOptionJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeTemplateJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeTemplateJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeTemplateMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AttributeTemplatePersistenceAdapter implements AttributeTemplateRepository {

    private final AttributeTemplateJpaRepository jpaRepository;
    private final AttributeOptionJpaRepository optionRepository;

    @Override
    public Optional<AttributeTemplate> findById(AttributeTemplateId id) {
        return jpaRepository.findById(id.getValue()).map(entity -> {
            List<AttributeOptionJpaEntity> options =
                    optionRepository.findByTemplateId(entity.getId());
            return AttributeTemplateMapper.toDomain(entity, options);
        });
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public List<AttributeTemplate> findAll() {
        List<AttributeTemplateJpaEntity> entities = jpaRepository.findAll();

        Map<String, List<AttributeOptionJpaEntity>> optionsByTemplate =
                optionRepository.findAll().stream()
                        .collect(Collectors.groupingBy(AttributeOptionJpaEntity::getTemplateId));

        return entities.stream()
                .map(entity -> AttributeTemplateMapper.toDomain(
                        entity,
                        optionsByTemplate.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Override
    public List<AttributeTemplate> findAllByScope(AttributeScope scope) {
        List<AttributeTemplateJpaEntity> entities = jpaRepository.findAllByScope(scope);

        Map<String, List<AttributeOptionJpaEntity>> optionsByTemplate =
                optionRepository.findAll().stream()
                        .collect(Collectors.groupingBy(AttributeOptionJpaEntity::getTemplateId));

        return entities.stream()
                .map(entity -> AttributeTemplateMapper.toDomain(
                        entity,
                        optionsByTemplate.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public void save(AttributeTemplate template) {
        jpaRepository.save(AttributeTemplateMapper.toJpaEntity(template));

        optionRepository.deleteByTemplateId(template.getId().getValue());
        List<AttributeOptionJpaEntity> optionEntities =
                AttributeTemplateMapper.toOptionEntities(template);
        if (!optionEntities.isEmpty()) {
            optionRepository.saveAll(optionEntities);
        }
    }

    @Override
    @Transactional
    public void delete(AttributeTemplateId id) {
        optionRepository.deleteByTemplateId(id.getValue());
        jpaRepository.deleteById(id.getValue());
    }
}
