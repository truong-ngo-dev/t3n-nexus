package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Ngừng tùy chọn: chỉ chặn chọn mới. Ngừng tùy chọn cuối cùng được phép, mẫu thành không dùng được. */
@Service
@RequiredArgsConstructor
public class DeactivateAttributeOption implements CommandHandler<DeactivateAttributeOption.Command, TemplateView> {

    private final TemplateUpdater updater;

    @Override
    public TemplateView handle(Command c) {
        return updater.update(c.actor(), c.templateId(),
                (t, ctx) -> t.deactivateOption(AttributeOptionId.of(c.optionId()), ctx));
    }

    public record Command(CatalogActor actor, UUID templateId, UUID optionId) {}
}
