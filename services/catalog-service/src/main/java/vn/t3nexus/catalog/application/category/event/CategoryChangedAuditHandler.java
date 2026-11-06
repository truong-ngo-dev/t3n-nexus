package vn.t3nexus.catalog.application.category.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.application.CatalogAuditActions;
import vn.t3nexus.catalog.domain.category.CategoryChanged;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.service.EventHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi bản ghi kiểm toán cho thay đổi danh mục: tạo, ngừng, xóa có hành động riêng; đổi tên, sắp xếp, thay danh sách gán
 * và bật lại gộp vào {@code CATEGORY_UPDATED} (kèm {@code change}). Xóa ghi tên và cha vì sau đó không còn chỗ tra.
 */
@Component
@RequiredArgsConstructor
public class CategoryChangedAuditHandler implements EventHandler<CategoryChanged> {

    private final AuditRecorder auditRecorder;

    @Override
    public void handle(CategoryChanged event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", event.version());
        String action;
        switch (event.change()) {
            case CREATED -> {
                action = CatalogAuditActions.CATEGORY_CREATED;
                details.put("name", event.name());
                putIfPresent(details, "parentId", event.parentId());
                details.put("level", event.level());
            }
            case DEACTIVATED -> action = CatalogAuditActions.CATEGORY_DEACTIVATED;
            case DELETED -> {
                action = CatalogAuditActions.CATEGORY_DELETED;
                details.put("name", event.name());
                putIfPresent(details, "parentId", event.parentId());
            }
            case RENAMED -> {
                action = CatalogAuditActions.CATEGORY_UPDATED;
                details.put("change", event.change().name());
                details.put("oldName", event.oldName());
                details.put("newName", event.name());
            }
            case REORDERED -> {
                action = CatalogAuditActions.CATEGORY_UPDATED;
                details.put("change", event.change().name());
                putIfPresent(details, "parentId", event.parentId());
            }
            case ASSIGNMENTS_REPLACED -> {
                action = CatalogAuditActions.CATEGORY_UPDATED;
                details.put("change", event.change().name());
                details.put("count", event.count());
            }
            default -> {
                action = CatalogAuditActions.CATEGORY_UPDATED;
                details.put("change", event.change().name());
            }
        }
        auditRecorder.record(action,
                new AuditActor(event.actorId(), CatalogAuditActions.ADMIN_ROLE),
                new AuditTarget(CatalogAuditActions.TARGET_CATEGORY, event.categoryId().getValue().toString()),
                details);
    }

    private static void putIfPresent(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value.toString());
        }
    }

    @Override
    public Class<CategoryChanged> getEventType() {
        return CategoryChanged.class;
    }

    @Override
    public int getOrder() {
        return CatalogAuditActions.AUDIT_HANDLER_ORDER;
    }
}
