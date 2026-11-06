package vn.t3nexus.catalog.application.category;

import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Kho trong bộ nhớ cho test use case. Lưu bản sao có phiên bản tăng như cơ sở dữ liệu thật (0 khi lưu lần đầu). */
public class FakeCategoryRepository implements CategoryRepository {

    private final Map<UUID, Category> store = new LinkedHashMap<>();
    public int saveCount;
    public int deleteCount;

    @Override
    public Optional<Category> findById(CategoryId id) {
        return Optional.ofNullable(store.get(id.getValue()));
    }

    @Override
    public boolean existsByParentId(CategoryId parentId) {
        return store.values().stream().anyMatch(c -> parentId.equals(c.getParentId()));
    }

    @Override
    public boolean existsByParentAndNameKey(CategoryId parentId, String nameKey) {
        return store.values().stream()
                .anyMatch(c -> sameParent(c, parentId) && c.getNameKey().equals(nameKey));
    }

    @Override
    public boolean existsByParentAndNameKeyExcludingId(CategoryId parentId, String nameKey, CategoryId excludingId) {
        return store.values().stream()
                .anyMatch(c -> sameParent(c, parentId) && c.getNameKey().equals(nameKey)
                        && !c.getId().equals(excludingId));
    }

    @Override
    public boolean hasInactiveAncestor(CategoryId id) {
        Category current = store.get(id.getValue());
        while (current != null && current.getParentId() != null) {
            current = store.get(current.getParentId().getValue());
            if (current != null && !current.isActive()) return true;
        }
        return false;
    }

    @Override
    public List<Category> findSiblings(CategoryId parentId) {
        return store.values().stream()
                .filter(c -> sameParent(c, parentId))
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();
    }

    @Override
    public List<Category> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public void save(Category c) {
        saveCount++;
        store.put(c.getId().getValue(), Category.reconstitute(c.getId(), c.getName(), c.getNameKey(), c.getParentId(),
                c.getLevel(), c.getSortOrder(), c.getStatus(), c.getAssignments(), c.nextVersion(), c.getCreatedAt(),
                c.getUpdatedAt()));
    }

    @Override
    public void delete(CategoryId id) {
        deleteCount++;
        store.remove(id.getValue());
    }

    public Category stored(UUID id) {
        return store.get(id);
    }

    private static boolean sameParent(Category c, CategoryId parentId) {
        return parentId == null ? c.getParentId() == null : parentId.equals(c.getParentId());
    }
}
