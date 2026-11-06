package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.catalog.application.CatalogAuditActions;
import vn.t3nexus.lib.audit.AuditActionCatalog;
import vn.t3nexus.lib.audit.AuditActionDeclaration;

import java.util.Set;

/**
 * Danh mục hành động kiểm toán của catalog-service và khóa dữ kiện cho phép của từng hành động (tech.md T4).
 * Hành động hay khóa chưa khai báo làm thư viện ném lỗi và rollback cả giao dịch. Mỗi aggregate thêm khai báo của mình
 * khi làm xong.
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditActionCatalog auditActionCatalog() {
        return AuditActionCatalog.of(
                new AuditActionDeclaration(CatalogAuditActions.ATTRIBUTE_CREATED,
                        Set.of("version", "name", "inputType")),
                new AuditActionDeclaration(CatalogAuditActions.ATTRIBUTE_UPDATED,
                        Set.of("version", "change", "optionId", "value")),
                new AuditActionDeclaration(CatalogAuditActions.ATTRIBUTE_DEACTIVATED,
                        Set.of("version")),
                new AuditActionDeclaration(CatalogAuditActions.BRAND_CREATED,
                        Set.of("version", "name")),
                new AuditActionDeclaration(CatalogAuditActions.BRAND_UPDATED,
                        Set.of("version", "change", "oldName", "newName")),
                new AuditActionDeclaration(CatalogAuditActions.BRAND_DEACTIVATED,
                        Set.of("version")),
                new AuditActionDeclaration(CatalogAuditActions.CATEGORY_CREATED,
                        Set.of("version", "name", "parentId", "level")),
                new AuditActionDeclaration(CatalogAuditActions.CATEGORY_UPDATED,
                        Set.of("version", "change", "oldName", "newName", "parentId", "count")),
                new AuditActionDeclaration(CatalogAuditActions.CATEGORY_DEACTIVATED,
                        Set.of("version")),
                new AuditActionDeclaration(CatalogAuditActions.CATEGORY_DELETED,
                        Set.of("version", "name", "parentId"))
        );
    }
}
