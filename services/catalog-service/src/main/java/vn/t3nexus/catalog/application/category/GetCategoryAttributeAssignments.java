package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.category.Discovery;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Xem assignment (quản trị) — trả ĐỦ, kể cả template đã tắt, để admin sửa (PUT lại toàn bộ) không làm rơi
 *  mất assignment cũ như {@link GetCategoryAttributes} (chỉ trả template dùng được, dành cho seller/buyer). */
@Service
@RequiredArgsConstructor
public class GetCategoryAttributeAssignments
        implements QueryHandler<GetCategoryAttributeAssignments.Query, GetCategoryAttributeAssignments.Result> {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        Category category = categoryRepository.findById(CategoryId.of(query.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        List<CategoryAttributeAssignment> assignments = category.getAssignments();
        List<AttributeTemplateId> templateIds = assignments.stream()
                .map(CategoryAttributeAssignment::getAttributeTemplateId)
                .toList();
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository.findAllByIds(templateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        List<AssignmentDetail> items = assignments.stream()
                .sorted(Comparator.comparingInt(CategoryAttributeAssignment::getDisplayOrder))
                .map(a -> {
                    AttributeTemplate t = templatesById.get(a.getAttributeTemplateId().getValue());
                    return new AssignmentDetail(
                            t.getId().getValue(), t.getName(), t.getDisplayName(), t.getInputType(), t.getStatus(),
                            a.isRequired(), a.getDisplayOrder(), a.getConstraints(), a.getDiscovery());
                })
                .toList();

        return new Result(items);
    }

    public record Query(String categoryId) {}

    public record Result(List<AssignmentDetail> items) {}

    public record AssignmentDetail(
            String templateId,
            String name,
            String displayName,
            InputType inputType,
            AttributeTemplateStatus templateStatus,
            boolean required,
            int displayOrder,
            AttributeConstraints constraints,
            Discovery discovery
    ) {}
}
