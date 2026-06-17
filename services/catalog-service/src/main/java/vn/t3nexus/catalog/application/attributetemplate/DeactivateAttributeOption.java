package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.*;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateAttributeOption
        implements CommandHandler<DeactivateAttributeOption.Command, DeactivateAttributeOption.Result> {

    private final AttributeTemplateRepository templateRepository;
    private final AttributeTemplateDomainService domainService;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_ATTRIBUTES, allEntries = true)
    public Result handle(Command command) {
        AttributeOptionId optionId = AttributeOptionId.of(command.optionId());

        domainService.validateOptionNotUsedByVariant(optionId);

        AttributeTemplate template = templateRepository.findById(AttributeTemplateId.of(command.templateId()))
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND));

        template.deactivateOption(optionId);
        templateRepository.save(template);

        log.info("[DeactivateAttributeOption] deactivated: templateId={}, optionId={}, traceId={}",
                command.templateId(), command.optionId(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String templateId, String optionId) {}

    public record Result() {}
}
