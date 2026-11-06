package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditActionCatalogTest {

    @Test
    void validDeclarationAndCatalogLookup() {
        AuditActionDeclaration decl = new AuditActionDeclaration("SELLER_PROFILE_REJECTED", Set.of("reason", "profileVersion"));
        AuditActionCatalog catalog = AuditActionCatalog.of(decl);

        Optional<AuditActionDeclaration> found = catalog.find("SELLER_PROFILE_REJECTED");
        assertThat(found).isPresent();
        assertThat(found.get().action()).isEqualTo("SELLER_PROFILE_REJECTED");
        assertThat(found.get().detailKeys()).containsExactlyInAnyOrder("reason", "profileVersion");

        assertThat(catalog.find("UNKNOWN_ACTION")).isEmpty();
    }

    @Test
    void forbiddenKeyInDeclarationIsRejected() {
        assertThatThrownBy(() -> new AuditActionDeclaration("USER_REGISTERED", Set.of("email")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email");
    }

    @Test
    void invalidActionPatternIsRejected() {
        assertThatThrownBy(() -> new AuditActionDeclaration("invalidAction", Set.of("key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("action");
    }

    @Test
    void duplicateActionInCatalogIsRejected() {
        AuditActionDeclaration decl1 = new AuditActionDeclaration("CATEGORY_CREATED", Set.of());
        AuditActionDeclaration decl2 = new AuditActionDeclaration("CATEGORY_CREATED", Set.of("note"));

        assertThatThrownBy(() -> AuditActionCatalog.of(decl1, decl2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CATEGORY_CREATED");
    }
}
