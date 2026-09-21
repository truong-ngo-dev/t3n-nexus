package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateDomainService;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateAttributeTemplate
        implements CommandHandler<DeactivateAttributeTemplate.Command, DeactivateAttributeTemplate.Result> {

    private final AttributeTemplateRepository templateRepository;
    private final AttributeTemplateDomainService domainService;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_ATTRIBUTES, allEntries = true)
    public Result handle(Command command) {
        AttributeTemplateId id = AttributeTemplateId.of(command.id());
        AttributeTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND));

        domainService.validateTemplateDeactivatable(id);

        template.deactivate();
        templateRepository.save(template);

        log.info("[DeactivateAttributeTemplate] deactivated: templateId={}, traceId={}",
                command.id(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String id) {}

    public record Result() {}
}
