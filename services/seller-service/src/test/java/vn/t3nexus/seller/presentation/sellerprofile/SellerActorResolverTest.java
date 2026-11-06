package vn.t3nexus.seller.presentation.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử đơn vị cho SellerActorResolver.
 */
class SellerActorResolverTest {

    @Test
    @DisplayName("sub là UUID thì accountId đúng và vai USER")
    void testValidSubAndRole() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(accountId.toString())
                .claim("role", "USER")
                .build();

        SellerActor actor = SellerActorResolver.from(jwt);
        assertThat(actor.accountId()).isEqualTo(accountId);
        assertThat(actor.role()).isEqualTo("USER");
        assertThat(actor.isUser()).isTrue();
    }

    @Test
    @DisplayName("vai admin thành ADMIN")
    void testNormalizeRole() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(accountId.toString())
                .claim("role", "admin")
                .build();

        SellerActor actor = SellerActorResolver.from(jwt);
        assertThat(actor.accountId()).isEqualTo(accountId);
        assertThat(actor.role()).isEqualTo("ADMIN");
        assertThat(actor.isUser()).isFalse();
    }

    @Test
    @DisplayName("không có claim role thì isUser sai")
    void testMissingRole() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(accountId.toString())
                .build();

        SellerActor actor = SellerActorResolver.from(jwt);
        assertThat(actor.accountId()).isEqualTo(accountId);
        assertThat(actor.role()).isEqualTo("");
        assertThat(actor.isUser()).isFalse();
    }

    @Test
    @DisplayName("sub không phải UUID thì 401 và thông báo không chứa sub")
    void testInvalidSub() {
        String invalidSub = "not-a-valid-uuid-12345";
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(invalidSub)
                .claim("role", "USER")
                .build();

        assertThatThrownBy(() -> SellerActorResolver.from(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(401);
                    assertThat(rse.getMessage()).doesNotContain(invalidSub);
                });
    }

    @Test
    @DisplayName("không có sub thì 401")
    void testMissingSub() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("role", "USER")
                .claim("other", "val")
                .build();

        assertThatThrownBy(() -> SellerActorResolver.from(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(401);
                });
    }
}
