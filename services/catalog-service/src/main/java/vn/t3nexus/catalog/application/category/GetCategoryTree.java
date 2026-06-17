package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
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
        List<Category> allCategories = categoryRepository.findAll();

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
                .sorted(Comparator.comparing(Category::getName))
                .map(c -> buildNode(c, childrenByParent))
                .toList();

        return new Result(tree);
    }

    private CategoryTreeNode buildNode(Category category, Map<String, List<Category>> childrenByParent) {
        List<Category> childCategories = childrenByParent
                .getOrDefault(category.getId().getValue(), List.of());

        List<CategoryTreeNode> children = childCategories.stream()
                .sorted(Comparator.comparing(Category::getName))
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
