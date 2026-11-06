package vn.t3nexus.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vai của người thực hiện: lệnh ghi cấu trúc dành cho ADMIN, lệnh của người bán dành cho USER (INV-CAT-30). */
class CatalogActorTest {

    private static CatalogActor actor(String role) {
        return new CatalogActor(UUID.randomUUID(), role);
    }

    @Test
    @DisplayName("requireSeller: USER qua, không phân biệt hoa thường và khoảng trắng")
    void sellerPasses() {
        actor("USER").requireSeller();
        actor(" user ").requireSeller();
        assertThat(actor("USER").isSeller()).isTrue();
    }

    @Test
    @DisplayName("requireSeller: ADMIN, vai rỗng hoặc vai khác bị từ chối 403 CATALOG_ROLE_NOT_ALLOWED")
    void nonSellerRejected() {
        for (String role : new String[]{"ADMIN", "", null, "SHIPPER", "USERS"}) {
            assertThatThrownBy(() -> actor(role).requireSeller())
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED));
        }
    }

    @Test
    @DisplayName("requireAdmin vẫn chỉ cho ADMIN; USER bị từ chối")
    void adminUnchanged() {
        actor("ADMIN").requireAdmin();
        assertThatThrownBy(() -> actor("USER").requireAdmin())
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED));
    }
}
