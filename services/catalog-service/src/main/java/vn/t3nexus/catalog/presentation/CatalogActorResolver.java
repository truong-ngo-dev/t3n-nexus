package vn.t3nexus.catalog.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.catalog.domain.CatalogActor;

import java.util.UUID;

/** Dựng người thực hiện từ token: mã tài khoản ở {@code sub}, vai ở claim {@code role}. */
public final class CatalogActorResolver {

    private CatalogActorResolver() {
    }

    public static CatalogActor from(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String role = jwt.getClaimAsString("role");
        return new CatalogActor(accountId, role == null ? "" : role);
    }
}
