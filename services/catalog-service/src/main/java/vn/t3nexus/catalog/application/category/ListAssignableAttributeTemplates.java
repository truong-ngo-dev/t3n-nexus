package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Droplist khi Admin gán thêm attribute cho danh mục lá — chỉ template DÙNG ĐƯỢC và CHƯA gán vào danh mục này. */
@Service
@RequiredArgsConstructor
public class ListAssignableAttributeTemplates
        implements QueryHandler<ListAssignableAttributeTemplates.Query, ListAssignableAttributeTemplates.Result> {

    private static final int MAX_RESULTS = 500;

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        Category category = categoryRepository.findById(CategoryId.of(query.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        Set<String> assignedIds = category.getAssignments().stream()
                .map(a -> a.getAttributeTemplateId().getValue())
                .collect(Collectors.toSet());

        List<TemplateOption> options = attributeTemplateRepository
                .search(query.keyword(), null, AttributeTemplateStatus.ACTIVE, 0, MAX_RESULTS).stream()
                .filter(AttributeTemplate::isUsable)
                .filter(t -> !assignedIds.contains(t.getId().getValue()))
                .map(t -> new TemplateOption(t.getId().getValue(), t.getDisplayName(), t.getInputType()))
                .toList();

        return new Result(options);
    }

    public record Query(String categoryId, String keyword) {}

    public record Result(List<TemplateOption> options) {}

    public record TemplateOption(String id, String displayName, InputType inputType) {}
}
