package vn.t3nexus.seller.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.audit.AuditActionCatalog;
import vn.t3nexus.lib.audit.AuditActionDeclaration;
import vn.t3nexus.seller.application.sellerprofile.event.SellerAuditActions;

import java.util.Set;

/**
 * Cấu hình danh mục các hành động kiểm toán cho dịch vụ người bán.
 */
@Configuration
public class AuditConfig {

    public AuditConfig() {
    }

    /**
     * Khởi tạo danh mục hành động kiểm toán được hỗ trợ.
     */
    @Bean
    public AuditActionCatalog auditActionCatalog() {
        return AuditActionCatalog.of(
                new AuditActionDeclaration(
                        SellerAuditActions.PROFILE_APPROVED,
                        Set.of("viewedVersion", "sellerAccountId")
                ),
                new AuditActionDeclaration(
                        SellerAuditActions.PROFILE_REJECTED,
                        Set.of("viewedVersion", "sellerAccountId", "rejectionReason")
                )
        );
    }
}
