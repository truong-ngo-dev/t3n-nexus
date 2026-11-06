package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Khuôn chung của lệnh sửa một mẫu có sẵn: kiểm vai ADMIN, đọc mẫu, gọi hành vi miền, lưu, phát event đồng bộ
 * (kiểm toán và hộp thư cùng giao dịch). Mỗi use case chỉ nêu hành vi miền cần gọi.
 */
@Component
@RequiredArgsConstructor
public class TemplateUpdater {

    private final AttributeTemplateRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Transactional
    public TemplateView update(CatalogActor actor, UUID templateId, BiConsumer<AttributeTemplate, ChangeContext> change) {
        actor.requireAdmin();
        AttributeTemplate template = repository.findById(AttributeTemplateId.of(templateId)).orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NOT_FOUND));

        change.accept(template, new ChangeContext(actor, idGenerator.generate(), clock.instant()));
        long version = template.nextVersion();
        repository.save(template);

        eventDispatcher.dispatchAll(template.getDomainEvents());
        template.clearDomainEvents();
        return TemplateView.of(template, version);
    }
}
