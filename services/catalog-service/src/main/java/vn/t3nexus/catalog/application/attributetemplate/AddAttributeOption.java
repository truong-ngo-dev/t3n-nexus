package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.util.UUID;

/** Thêm tùy chọn vào mẫu kiểu chọn đang dùng; giá trị duy nhất trong mẫu sau chuẩn hóa (INV-CAT-11). */
@Service
@RequiredArgsConstructor
public class AddAttributeOption implements CommandHandler<AddAttributeOption.Command, TemplateView> {

    private final TemplateUpdater updater;
    private final IdGenerator idGenerator;

    @Override
    public TemplateView handle(Command c) {
        AttributeOptionId optionId = AttributeOptionId.of(idGenerator.generate());
        return updater.update(c.actor(), c.templateId(),
                (t, ctx) -> t.addOption(optionId, c.value(), c.displayValue(), ctx));
    }

    public record Command(CatalogActor actor, UUID templateId, String value, String displayValue) {}
}
