package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Bật lại tùy chọn (ưu tiên hơn tạo tùy chọn mới cùng giá trị, tránh hai mã cho một khái niệm). */
@Service
@RequiredArgsConstructor
public class ActivateAttributeOption implements CommandHandler<ActivateAttributeOption.Command, TemplateView> {

    private final TemplateUpdater updater;

    @Override
    public TemplateView handle(Command c) {
        return updater.update(c.actor(), c.templateId(),
                (t, ctx) -> t.activateOption(AttributeOptionId.of(c.optionId()), ctx));
    }

    public record Command(CatalogActor actor, UUID templateId, UUID optionId) {}
}
