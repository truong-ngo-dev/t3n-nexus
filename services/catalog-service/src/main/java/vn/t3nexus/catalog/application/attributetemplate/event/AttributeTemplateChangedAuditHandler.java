package vn.t3nexus.catalog.application.attributetemplate.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.application.CatalogAuditActions;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateChanged;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.service.EventHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi bản ghi kiểm toán cho thay đổi mẫu: tạo, ngừng, và mọi thay đổi còn lại gộp vào {@code ATTRIBUTE_UPDATED}
 * (kèm {@code change}). Dữ kiện chỉ gồm hành vi và giá trị của hành vi.
 */
@Component
@RequiredArgsConstructor
public class AttributeTemplateChangedAuditHandler implements EventHandler<AttributeTemplateChanged> {

    private final AuditRecorder auditRecorder;

    @Override
    public void handle(AttributeTemplateChanged event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", event.version());
        String action;
        switch (event.change()) {
            case CREATED -> {
                action = CatalogAuditActions.ATTRIBUTE_CREATED;
                details.put("name", event.value());
                details.put("inputType", event.inputType().name());
            }
            case DEACTIVATED -> action = CatalogAuditActions.ATTRIBUTE_DEACTIVATED;
            default -> {
                action = CatalogAuditActions.ATTRIBUTE_UPDATED;
                details.put("change", event.change().name());
                if (event.optionId() != null) {
                    details.put("optionId", event.optionId().toString());
                    details.put("value", event.value());
                }
            }
        }
        auditRecorder.record(action,
                new AuditActor(event.actorId(), CatalogAuditActions.ADMIN_ROLE),
                new AuditTarget(CatalogAuditActions.TARGET_ATTRIBUTE_TEMPLATE, event.templateId().getValue().toString()),
                details);
    }

    @Override
    public Class<AttributeTemplateChanged> getEventType() {
        return AttributeTemplateChanged.class;
    }

    @Override
    public int getOrder() {
        return CatalogAuditActions.AUDIT_HANDLER_ORDER;
    }
}
