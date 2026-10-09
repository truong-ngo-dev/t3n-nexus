package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.category.Discovery;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thay toàn bộ danh sách attribute assignment của 1 category (leaf/L3) trong 1 lần gọi — assign/update/
 * remove 1 attribute đều quy về gọi lại use case này với danh sách mong muốn cuối cùng (client tự
 * {@link GetCategoryAttributes} rồi sửa trước khi gọi), khớp đúng cách persistence vốn đã hoạt động
 * (xoá hết + insert lại toàn bộ mỗi lần save Category).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplaceCategoryAttributeAssignments
        implements CommandHandler<ReplaceCategoryAttributeAssignments.Command, ReplaceCategoryAttributeAssignments.Result> {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_ATTRIBUTES, key = "#command.categoryId()")
    public Result handle(Command command) {
        Category category = categoryRepository.findById(CategoryId.of(command.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        // INV-CAT-93: chỉ thuộc tính MỚI gán phải đang dùng được; thuộc tính đã gán mà nay đã tắt vẫn giữ/sửa được
        // (sửa cái đã có, không phải chọn mới — analysis.md AGG-CAT-03). "Chỉ danh mục lá" kiểm trong aggregate.
        Set<String> currentlyAssignedIds = category.getAssignments().stream()
                .map(a -> a.getAttributeTemplateId().getValue())
                .collect(Collectors.toSet());

        List<AttributeTemplateId> templateIds = command.assignments().stream()
                .map(item -> AttributeTemplateId.of(item.templateId()))
                .toList();
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository.findAllByIds(templateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        List<CategoryAttributeAssignment> assignments = command.assignments().stream()
                .map(item -> {
                    AttributeTemplate template = templatesById.get(item.templateId());
                    if (template == null) {
                        throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND);
                    }
                    boolean isNew = !currentlyAssignedIds.contains(item.templateId());
                    if (isNew && !template.isUsable()) {
                        throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
                    }
                    return CategoryAttributeAssignment.create(
                            template.getId(), template.getInputType(), item.required(), item.displayOrder(),
                            item.constraints(), item.discovery());
                })
                .toList();

        category.replaceAssignments(assignments);
        categoryRepository.save(category);
        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();

        log.info("[ReplaceCategoryAttributeAssignments] replaced: categoryId={}, count={}, traceId={}",
                command.categoryId(), assignments.size(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String categoryId, List<AttributeAssignmentItem> assignments) {}

    public record AttributeAssignmentItem(
            String templateId,
            boolean required,
            int displayOrder,
            AttributeConstraints constraints,
            Discovery discovery
    ) {}

    public record Result() {}
}
