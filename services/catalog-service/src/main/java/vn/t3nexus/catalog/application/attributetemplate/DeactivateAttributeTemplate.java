package vn.t3nexus.catalog.application.attributetemplate;

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
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateAttributeTemplate
        implements CommandHandler<DeactivateAttributeTemplate.Command, DeactivateAttributeTemplate.Result> {

    private final AttributeTemplateRepository templateRepository;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_ATTRIBUTES, allEntries = true)
    public Result handle(Command command) {
        AttributeTemplateId id = AttributeTemplateId.of(command.id());
        AttributeTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND));

        // Không guard: tắt chỉ chặn lựa chọn mới; "bắt buộc có hiệu lực" bỏ qua template không dùng được nên không
        // còn trạng thái "bắt buộc nhưng không chọn được" (analysis.md AGG-CAT-01).

        template.deactivate();
        templateRepository.save(template);

        log.info("[DeactivateAttributeTemplate] deactivated: templateId={}, traceId={}",
                command.id(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String id) {}

    public record Result() {}
}
