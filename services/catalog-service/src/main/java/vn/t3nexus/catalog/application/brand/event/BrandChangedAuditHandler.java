package vn.t3nexus.catalog.application.brand.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.application.CatalogAuditActions;
import vn.t3nexus.catalog.domain.brand.BrandChanged;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.service.EventHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi bản ghi kiểm toán cho thay đổi thương hiệu: tạo, ngừng, và đổi tên hoặc bật lại gộp vào {@code BRAND_UPDATED}
 * (kèm {@code change}). Thương hiệu không ra hộp thư ở C1a, nên đây là handler duy nhất của event này.
 */
@Component
@RequiredArgsConstructor
public class BrandChangedAuditHandler implements EventHandler<BrandChanged> {

    private final AuditRecorder auditRecorder;

    @Override
    public void handle(BrandChanged event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", event.version());
        String action;
        switch (event.change()) {
            case CREATED -> {
                action = CatalogAuditActions.BRAND_CREATED;
                details.put("name", event.name());
            }
            case DEACTIVATED -> action = CatalogAuditActions.BRAND_DEACTIVATED;
            case RENAMED -> {
                action = CatalogAuditActions.BRAND_UPDATED;
                details.put("change", event.change().name());
                details.put("oldName", event.oldName());
                details.put("newName", event.name());
            }
            default -> {
                action = CatalogAuditActions.BRAND_UPDATED;
                details.put("change", event.change().name());
            }
        }
        auditRecorder.record(action,
                new AuditActor(event.actorId(), CatalogAuditActions.ADMIN_ROLE),
                new AuditTarget(CatalogAuditActions.TARGET_BRAND, event.brandId().getValue().toString()),
                details);
    }

    @Override
    public Class<BrandChanged> getEventType() {
        return BrandChanged.class;
    }

    @Override
    public int getOrder() {
        return CatalogAuditActions.AUDIT_HANDLER_ORDER;
    }
}
