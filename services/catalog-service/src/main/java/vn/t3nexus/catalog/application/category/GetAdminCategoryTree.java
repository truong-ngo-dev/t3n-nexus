package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.category.CategoryStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cây đầy đủ cho quản trị: mọi trạng thái, kèm cờ {@code usable} (chính nó và mọi tổ tiên đang dùng, INV-CAT-97),
 * {@code sortOrder} và {@code version}. Cây tối đa ba cấp nên cờ tính ngay khi đi xuống, không cần tra thêm.
 */
@Service
@RequiredArgsConstructor
public class GetAdminCategoryTree implements QueryHandler<GetAdminCategoryTree.Query, List<GetAdminCategoryTree.Node>> {

    private final CategoryRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Node> handle(Query query) {
        query.actor().requireAdmin();
        Map<UUID, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : repository.findAll()) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId().getValue(), k -> new ArrayList<>())
                        .add(category);
            }
        }
        return sorted(roots).stream().map(c -> node(c, true, childrenByParent)).toList();
    }

    private Node node(Category category, boolean ancestorsActive, Map<UUID, List<Category>> childrenByParent) {
        boolean usable = ancestorsActive && category.isActive();
        List<Node> children = sorted(childrenByParent.getOrDefault(category.getId().getValue(), List.of())).stream()
                .map(child -> node(child, usable, childrenByParent))
                .toList();
        return new Node(category.getId().getValue(), category.getName(), category.getLevel().getValue(),
                category.getSortOrder(), category.getStatus(), usable, category.version(), children);
    }

    private static List<Category> sorted(List<Category> categories) {
        return categories.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(c -> c.getId().getValue()))
                .toList();
    }

    public record Query(CatalogActor actor) {}

    public record Node(UUID id, String name, int level, int sortOrder, CategoryStatus status, boolean usable,
                       long version, List<Node> children) {}
}
