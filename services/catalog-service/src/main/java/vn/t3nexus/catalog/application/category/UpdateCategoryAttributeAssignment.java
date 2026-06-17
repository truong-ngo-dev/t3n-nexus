package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCategoryAttributeAssignment
        implements CommandHandler<UpdateCategoryAttributeAssignment.Command, UpdateCategoryAttributeAssignment.Result> {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_ATTRIBUTES, key = "#command.categoryId()")
    public Result handle(Command command) {
        Category category = categoryRepository.findById(CategoryId.of(command.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        category.updateAssignment(new CategoryAttributeAssignment(
                AttributeTemplateId.of(command.templateId()),
                command.variantDefining(),
                command.required(),
                command.filterable(),
                command.displayOrder()
        ));

        categoryRepository.save(category);

        log.info("[UpdateCategoryAttributeAssignment] updated: categoryId={}, templateId={}, traceId={}",
                command.categoryId(), command.templateId(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(
            String categoryId,
            String templateId,
            boolean variantDefining,
            boolean required,
            boolean filterable,
            int displayOrder
    ) {}

    public record Result() {}
}
