package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;

/** Quản trị tạo mẫu thuộc tính (CAP-CAT-02). Tên trùng sau chuẩn hóa thì từ chối (INV-CAT-10). */
@Service
@RequiredArgsConstructor
public class CreateAttributeTemplate
        implements CommandHandler<CreateAttributeTemplate.Command, TemplateView> {

    private final AttributeTemplateRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public TemplateView handle(Command command) {
        command.actor().requireAdmin();
        if (repository.existsByName(AttributeTemplate.validName(command.name()))) {
            throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NAME_TAKEN);
        }

        ChangeContext ctx = new ChangeContext(command.actor(), idGenerator.generate(), clock.instant());
        AttributeTemplate template = AttributeTemplate.create(
                AttributeTemplateId.of(idGenerator.generate()), command.name(), command.displayName(),
                command.hint(), command.inputType(), command.unit(), ctx);
        long version = template.nextVersion();
        repository.save(template);

        eventDispatcher.dispatchAll(template.getDomainEvents());
        template.clearDomainEvents();
        return TemplateView.of(template, version);
    }

    public record Command(CatalogActor actor, String name, String displayName, String hint,
                          InputType inputType, String unit) {}
}
