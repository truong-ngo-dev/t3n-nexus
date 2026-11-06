package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.List;
import java.util.UUID;

/** Sắp lại thứ tự tùy chọn: danh sách phải nêu mọi tùy chọn của mẫu đúng một lần. */
@Service
@RequiredArgsConstructor
public class ReorderAttributeOptions implements CommandHandler<ReorderAttributeOptions.Command, TemplateView> {

    private final TemplateUpdater updater;

    @Override
    public TemplateView handle(Command c) {
        List<AttributeOptionId> ordered = c.optionIds().stream().map(AttributeOptionId::of).toList();
        return updater.update(c.actor(), c.templateId(), (t, ctx) -> t.reorderOptions(ordered, ctx));
    }

    public record Command(CatalogActor actor, UUID templateId, List<UUID> optionIds) {}
}
