package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.*;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateAttributeTemplate
        implements CommandHandler<CreateAttributeTemplate.Command, CreateAttributeTemplate.Result> {

    private final AttributeTemplateRepository templateRepository;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    public Result handle(Command command) {
        if (templateRepository.existsByName(command.name())) {
            throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_NAME_EXISTS);
        }

        AttributeTemplateId id = AttributeTemplateId.of(ulidGenerator.generate());
        AttributeTemplate template = AttributeTemplate.create(
                id, command.name(), command.displayName(), command.inputType(), command.scope());
        templateRepository.save(template);

        log.info("[CreateAttributeTemplate] created: templateId={}, name={}, traceId={}",
                id.getValue(), command.name(), MDC.get("traceId"));

        return new Result(id.getValue());
    }

    public record Command(String name, String displayName, InputType inputType, AttributeScope scope) {}

    public record Result(String id) {}
}
