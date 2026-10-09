package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.category.CategoryStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cây quản trị — MỌI trạng thái (kèm cờ), để Admin thấy và bật lại danh mục đã tắt. Không cache — chỉ admin dùng. */
@Service
@RequiredArgsConstructor
public class GetAdminCategoryTree implements QueryHandler<GetAdminCategoryTree.Query, GetAdminCategoryTree.Result> {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
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
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(c -> buildNode(c, childrenByParent))
                .toList();

        return new Result(tree);
    }

    private CategoryTreeNode buildNode(Category category, Map<String, List<Category>> childrenByParent) {
        List<Category> childCategories = childrenByParent.getOrDefault(category.getId().getValue(), List.of());
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
                category.getStatus(),
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
            CategoryStatus status,
            List<CategoryTreeNode> children
    ) {}
}
