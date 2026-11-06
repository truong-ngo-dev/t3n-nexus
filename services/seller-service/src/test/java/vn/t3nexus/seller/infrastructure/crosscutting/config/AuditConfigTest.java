package vn.t3nexus.seller.infrastructure.crosscutting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.audit.AuditActionCatalog;
import vn.t3nexus.lib.audit.AuditActionDeclaration;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử đơn vị AuditConfig tạo danh mục AuditActionCatalog hợp lệ.
 */
class AuditConfigTest {

    @Test
    @DisplayName("AuditActionCatalog khai báo đúng SELLER_PROFILE_APPROVED và SELLER_PROFILE_REJECTED")
    void auditActionCatalogContainsCorrectDeclarations() {
        AuditConfig config = new AuditConfig();
        AuditActionCatalog catalog = config.auditActionCatalog();

        Optional<AuditActionDeclaration> approved = catalog.find("SELLER_PROFILE_APPROVED");
        assertThat(approved).isPresent();
        assertThat(approved.get().detailKeys()).isEqualTo(Set.of("viewedVersion", "sellerAccountId"));

        Optional<AuditActionDeclaration> rejected = catalog.find("SELLER_PROFILE_REJECTED");
        assertThat(rejected).isPresent();
        assertThat(rejected.get().detailKeys()).isEqualTo(Set.of("viewedVersion", "sellerAccountId", "rejectionReason"));

        Optional<AuditActionDeclaration> submitted = catalog.find("SELLER_PROFILE_SUBMITTED");
        assertThat(submitted).isEmpty();
    }
}
