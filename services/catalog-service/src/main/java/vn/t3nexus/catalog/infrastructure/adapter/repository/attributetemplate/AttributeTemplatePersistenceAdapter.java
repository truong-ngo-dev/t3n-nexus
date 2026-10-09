package vn.t3nexus.catalog.infrastructure.adapter.repository.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeOptionJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeOptionJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeTemplateJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeTemplateJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.attributetemplate.AttributeTemplateMapper;

import java.util.Collection;
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
    public List<AttributeTemplate> findAllByIds(Collection<AttributeTemplateId> ids) {
        List<String> rawIds = ids.stream().map(AttributeTemplateId::getValue).toList();
        if (rawIds.isEmpty()) return List.of();
        return withOptions(jpaRepository.findAllById(rawIds));
    }

    @Override
    public List<AttributeTemplate> search(String keyword, InputType inputType, AttributeTemplateStatus status,
                                          int page, int size) {
        return withOptions(jpaRepository.search(blankToNull(keyword), inputType, status, PageRequest.of(page, size)));
    }

    @Override
    public long count(String keyword, InputType inputType, AttributeTemplateStatus status) {
        return jpaRepository.countSearch(blankToNull(keyword), inputType, status);
    }

    /**
     * Lưu theo TỪNG DÒNG: template (optimistic lock qua {@code version} — 2 Admin sửa cùng lúc thì bên sau lỗi) + upsert
     * từng option theo id. KHÔNG xoá option nào: option không bao giờ bị xoá cứng (chỉ tắt), và dòng option đang được
     * {@code variant_combination_item} tham chiếu bằng FK nên xoá-rồi-chèn-lại là không an toàn.
     */
    @Override
    @Transactional
    public void save(AttributeTemplate template) {
        jpaRepository.save(AttributeTemplateMapper.toJpaEntity(template));
        List<AttributeOptionJpaEntity> optionEntities = AttributeTemplateMapper.toOptionEntities(template);
        if (!optionEntities.isEmpty()) {
            optionRepository.saveAll(optionEntities);
        }
    }

    // 1 round-trip cho option của đúng các template cần.
    private List<AttributeTemplate> withOptions(List<AttributeTemplateJpaEntity> entities) {
        if (entities.isEmpty()) return List.of();
        List<String> ids = entities.stream().map(AttributeTemplateJpaEntity::getId).toList();
        Map<String, List<AttributeOptionJpaEntity>> optionsByTemplate =
                optionRepository.findByTemplateIdIn(ids).stream()
                        .collect(Collectors.groupingBy(AttributeOptionJpaEntity::getTemplateId));
        return entities.stream()
                .map(entity -> AttributeTemplateMapper.toDomain(
                        entity, optionsByTemplate.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
