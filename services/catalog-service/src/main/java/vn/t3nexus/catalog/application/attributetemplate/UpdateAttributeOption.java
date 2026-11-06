package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Đổi tên hiển thị của tùy chọn, áp ngay mọi nơi đang dùng. Đổi nghĩa thì thêm tùy chọn mới và ngừng tùy chọn cũ. */
@Service
@RequiredArgsConstructor
public class UpdateAttributeOption implements CommandHandler<UpdateAttributeOption.Command, TemplateView> {

    private final TemplateUpdater updater;

    @Override
    public TemplateView handle(Command c) {
        return updater.update(c.actor(), c.templateId(),
                (t, ctx) -> t.updateOptionDisplayValue(AttributeOptionId.of(c.optionId()), c.displayValue(), ctx));
    }

    public record Command(CatalogActor actor, UUID templateId, UUID optionId, String displayValue) {}
}
