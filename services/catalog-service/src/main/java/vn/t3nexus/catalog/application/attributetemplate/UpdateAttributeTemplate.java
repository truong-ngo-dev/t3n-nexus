package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Sửa tên hiển thị và gợi ý nhập của mẫu; áp ngay mọi nơi đang dùng, bất kể trạng thái. */
@Service
@RequiredArgsConstructor
public class UpdateAttributeTemplate implements CommandHandler<UpdateAttributeTemplate.Command, TemplateView> {

    private final TemplateUpdater updater;

    @Override
    public TemplateView handle(Command c) {
        return updater.update(c.actor(), c.templateId(), (t, ctx) -> t.updateDetails(c.displayName(), c.hint(), ctx));
    }

    public record Command(CatalogActor actor, UUID templateId, String displayName, String hint) {}
}
