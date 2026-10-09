package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.category.CategoryStatus;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetCategoryTree implements QueryHandler<GetCategoryTree.Query, GetCategoryTree.Result> {

    private final CategoryRepository categoryRepository;

    @Override
    @Cacheable(value = CacheNames.CATEGORY_TREE, key = "'all'")
    public Result handle(Query query) {
        // Category INACTIVE bị ẩn hoàn toàn khỏi cây — không cascade status xuống children (mỗi node tự
        // quản lý độc lập), nhưng con của 1 node đã ẩn tự nhiên không xuất hiện được (không ai link tới
        // nó nữa) — đúng hiệu ứng "unreachable qua path cha" của Magento, không cần đổi status của con.
        List<Category> allCategories = categoryRepository.findAll().stream()
                .filter(c -> c.getStatus() == CategoryStatus.ACTIVE)
                .toList();

        Map<String, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();

        for (Category category : allCategories) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent
                        .computeIfAbsent(category.getParentId().getValue(), k -> new ArrayList<>())
                        .add(category);
            }
        }

        List<CategoryTreeNode> tree = roots.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(c -> buildNode(c, childrenByParent))
                .toList();

        return new Result(tree);
    }

    private CategoryTreeNode buildNode(Category category, Map<String, List<Category>> childrenByParent) {
        List<Category> childCategories = childrenByParent
                .getOrDefault(category.getId().getValue(), List.of());

        List<CategoryTreeNode> children = childCategories.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(child -> buildNode(child, childrenByParent))
                .toList();

        return new CategoryTreeNode(
                category.getId().getValue(),
                category.getName(),
                category.getSlug(),
                category.getLevel().getValue(),
                category.getImageUrl(),
                children
        );
    }

    public record Query() {}

    public record Result(List<CategoryTreeNode> roots) {}

    public record CategoryTreeNode(
            String id,
            String name,
            String slug,
            int level,
            String imageUrl,
            List<CategoryTreeNode> children
    ) {}
}
