package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Ngừng dùng mẫu: không gán được vào danh mục mới; giá trị sản phẩm đã lưu giữ nguyên (INV-CAT-12). */
@Service
@RequiredArgsConstructor
public class DeactivateAttributeTemplate implements CommandHandler<DeactivateAttributeTemplate.Command, TemplateView> {

    private final TemplateUpdater updater;

    @Override
    public TemplateView handle(Command c) {
        return updater.update(c.actor(), c.templateId(), (t, ctx) -> t.deactivate(ctx));
    }

    public record Command(CatalogActor actor, UUID templateId) {}
}
