package vn.t3nexus.catalog.infrastructure.adapter.repository.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AttributeTemplatePersistenceAdapter implements AttributeTemplateRepository {

    private final AttributeTemplateJpaRepository jpaRepository;
    private final AttributeOptionJpaRepository optionRepository;

    @Override
    public Optional<AttributeTemplate> findById(AttributeTemplateId id) {
        return jpaRepository.findById(id.getValue()).map(entity ->
                AttributeTemplateMapper.toDomain(entity, optionRepository.findByTemplateId(entity.getId())));
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public List<AttributeTemplate> findAllByIds(Collection<AttributeTemplateId> ids) {
        List<UUID> rawIds = ids.stream().map(AttributeTemplateId::getValue).toList();
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
     * Lưu theo từng dòng: dòng mẫu trước ({@code saveAndFlush}: khóa lạc quan qua {@code version}, vi phạm ràng buộc
     * nổ ngay trong giao dịch), rồi ghi từng tùy chọn theo mã. Không xóa tùy chọn nào: tùy chọn chỉ ngừng, không xóa.
     * Dòng mẫu luôn đổi ({@code updated_at}) nên mọi thay đổi tùy chọn đều tăng phiên bản của mẫu.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AttributeTemplate template) {
        jpaRepository.saveAndFlush(AttributeTemplateMapper.toJpaEntity(template));
        List<AttributeOptionJpaEntity> optionEntities = AttributeTemplateMapper.toOptionEntities(template);
        if (!optionEntities.isEmpty()) {
            optionRepository.saveAllAndFlush(optionEntities);
        }
    }

    private List<AttributeTemplate> withOptions(List<AttributeTemplateJpaEntity> entities) {
        if (entities.isEmpty()) return List.of();
        List<UUID> ids = entities.stream().map(AttributeTemplateJpaEntity::getId).toList();
        Map<UUID, List<AttributeOptionJpaEntity>> optionsByTemplate =
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
