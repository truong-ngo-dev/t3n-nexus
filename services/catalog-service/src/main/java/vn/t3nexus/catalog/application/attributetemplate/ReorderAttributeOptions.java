package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

/** Sắp lại thứ tự chuẩn của option — gửi đủ mọi option theo thứ tự mong muốn. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReorderAttributeOptions
        implements CommandHandler<ReorderAttributeOptions.Command, ReorderAttributeOptions.Result> {

    private final AttributeTemplateRepository templateRepository;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_ATTRIBUTES, allEntries = true)
    public Result handle(Command command) {
        AttributeTemplate template = templateRepository.findById(AttributeTemplateId.of(command.templateId()))
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND));

        template.reorderOptions(command.optionIds().stream().map(AttributeOptionId::of).toList());
        templateRepository.save(template);

        log.info("[ReorderAttributeOptions] reordered: templateId={}, count={}, traceId={}",
                command.templateId(), command.optionIds().size(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String templateId, List<String> optionIds) {}

    public record Result() {}
}
