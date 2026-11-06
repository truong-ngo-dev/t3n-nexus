package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Bật lại mẫu đã ngừng dùng. */
@Service
@RequiredArgsConstructor
public class ActivateAttributeTemplate implements CommandHandler<ActivateAttributeTemplate.Command, TemplateView> {

    private final TemplateUpdater updater;

    @Override
    public TemplateView handle(Command c) {
        return updater.update(c.actor(), c.templateId(), (t, ctx) -> t.activate(ctx));
    }

    public record Command(CatalogActor actor, UUID templateId) {}
}
