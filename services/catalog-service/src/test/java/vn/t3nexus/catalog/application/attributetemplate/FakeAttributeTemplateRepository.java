package vn.t3nexus.catalog.application.attributetemplate;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Kho trong bộ nhớ cho test use case. Lưu bản sao có phiên bản tăng như cơ sở dữ liệu thật (0 khi lưu lần đầu). */
public class FakeAttributeTemplateRepository implements AttributeTemplateRepository {

    private final Map<UUID, AttributeTemplate> store = new LinkedHashMap<>();
    public int saveCount;

    @Override
    public Optional<AttributeTemplate> findById(AttributeTemplateId id) {
        return Optional.ofNullable(store.get(id.getValue()));
    }

    @Override
    public boolean existsByName(String name) {
        return store.values().stream().anyMatch(t -> t.getName().equals(name));
    }

    @Override
    public List<AttributeTemplate> findAllByIds(Collection<AttributeTemplateId> ids) {
        return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
    }

    @Override
    public List<AttributeTemplate> search(String keyword, InputType inputType, AttributeTemplateStatus status,
                                          int page, int size) {
        return store.values().stream()
                .filter(t -> inputType == null || t.getInputType() == inputType)
                .filter(t -> status == null || t.getStatus() == status)
                .skip((long) page * size).limit(size).toList();
    }

    @Override
    public long count(String keyword, InputType inputType, AttributeTemplateStatus status) {
        return store.values().stream()
                .filter(t -> inputType == null || t.getInputType() == inputType)
                .filter(t -> status == null || t.getStatus() == status).count();
    }

    @Override
    public void save(AttributeTemplate t) {
        saveCount++;
        store.put(t.getId().getValue(), AttributeTemplate.reconstitute(t.getId(), t.getName(),
                t.getDisplayName(), t.getHint(), t.getInputType(), t.getUnit(), t.getStatus(), t.getOptions(),
                t.nextVersion(), t.getCreatedAt(), t.getUpdatedAt()));
    }

    public AttributeTemplate stored(UUID id) {
        return store.get(id);
    }
}
