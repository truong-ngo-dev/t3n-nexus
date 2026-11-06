package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cây danh mục để chọn (RM-CAT-01), cho mọi người đã đăng nhập: ba cấp lồng nhau theo {@code sortOrder}, chỉ nhánh dùng
 * được. Danh mục ngừng dùng bị ẩn cùng cả nhánh con của nó. Không ghi gì, không phát event.
 */
@Service
@RequiredArgsConstructor
public class GetCategoryTree implements QueryHandler<GetCategoryTree.Query, List<GetCategoryTree.Node>> {

    private final CategoryRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Node> handle(Query query) {
        Map<UUID, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new java.util.ArrayList<>();
        for (Category category : repository.findAll()) {
            if (!category.isActive()) {
                continue;
            }
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId().getValue(), k -> new java.util.ArrayList<>())
                        .add(category);
            }
        }
        return sorted(roots).stream().map(c -> node(c, childrenByParent)).toList();
    }

    // Con của danh mục ngừng dùng không bao giờ được tới: chỉ đi xuống từ các nút đang dùng.
    private Node node(Category category, Map<UUID, List<Category>> childrenByParent) {
        List<Node> children = sorted(childrenByParent.getOrDefault(category.getId().getValue(), List.of())).stream()
                .map(child -> node(child, childrenByParent))
                .toList();
        return new Node(category.getId().getValue(), category.getName(), category.getLevel().getValue(), children);
    }

    private static List<Category> sorted(List<Category> categories) {
        return categories.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(c -> c.getId().getValue()))
                .toList();
    }

    public record Query() {}

    public record Node(UUID id, String name, int level, List<Node> children) {}
}
