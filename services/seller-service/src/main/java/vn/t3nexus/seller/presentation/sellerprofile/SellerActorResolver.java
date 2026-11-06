package vn.t3nexus.seller.presentation.sellerprofile;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;

import java.util.UUID;

/**
 * Tiện ích phân giải thông tin SellerActor từ JWT token.
 */
public final class SellerActorResolver {

    private SellerActorResolver() {
    }

    public static SellerActor from(Jwt jwt) {
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
        if (role == null) {
            role = "";
        }

        return new SellerActor(accountId, role);
    }
}
