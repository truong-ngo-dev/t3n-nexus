package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
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
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Thay toàn bộ danh sách thuộc tính được gán cho một danh mục lá bằng một lần gọi (CAP-CAT-01): thêm, bỏ, đổi bắt buộc
 * và thứ tự đều làm bằng danh sách mong muốn cuối cùng. Chỉ mẫu MỚI thêm phải dùng được; mẫu đã gán từ trước mà nay
 * ngừng dùng vẫn giữ và sửa được (sửa cái đã có, không phải chọn mới). Ràng buộc phải hợp kiểu nhập của mẫu.
 */
@Service
@RequiredArgsConstructor
public class ReplaceCategoryAttributes implements CommandHandler<ReplaceCategoryAttributes.Command, List<AssignmentView>> {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository templateRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public List<AssignmentView> handle(Command command) {
        command.actor().requireAdmin();
        Category category = categoryRepository.findById(CategoryId.of(command.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        Set<UUID> alreadyAssigned = category.getAssignments().stream()
                .map(a -> a.getAttributeTemplateId().getValue())
                .collect(Collectors.toSet());
        List<AttributeTemplateId> requestedIds = command.items().stream()
                .map(item -> AttributeTemplateId.of(item.templateId()))
                .distinct()
                .toList();
        Map<UUID, AttributeTemplate> templates = templateRepository.findAllByIds(requestedIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), Function.identity()));

        List<CategoryAttributeAssignment> assignments = command.items().stream()
                .map(item -> toAssignment(item, templates.get(item.templateId()), alreadyAssigned))
                .toList();

        category.replaceAssignments(assignments,
                new ChangeContext(command.actor(), idGenerator.generate(), clock.instant()));
        categoryRepository.save(category);

        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        return toViews(category, templates.values());
    }

    private static CategoryAttributeAssignment toAssignment(Item item, AttributeTemplate template,
                                                            Set<UUID> alreadyAssigned) {
        if (template == null) {
            throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NOT_FOUND);
        }
        boolean isNew = !alreadyAssigned.contains(item.templateId());
        if (isNew && !template.isUsable()) {
            throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_INACTIVE);
        }
        return CategoryAttributeAssignment.create(template.getId(), template.getInputType(), item.required(),
                item.displayOrder(), item.constraints());
    }

    private static List<AssignmentView> toViews(Category category, Collection<AttributeTemplate> templates) {
        Map<UUID, AttributeTemplate> byId = new java.util.HashMap<>();
        templates.forEach(t -> byId.put(t.getId().getValue(), t));
        return category.getAssignments().stream()
                .sorted(Comparator.comparingInt(CategoryAttributeAssignment::getDisplayOrder))
                .map(a -> AssignmentView.of(a, byId.get(a.getAttributeTemplateId().getValue())))
                .toList();
    }

    public record Command(CatalogActor actor, UUID categoryId, List<Item> items) {}

    public record Item(UUID templateId, boolean required, int displayOrder, AttributeConstraints constraints) {}
}
