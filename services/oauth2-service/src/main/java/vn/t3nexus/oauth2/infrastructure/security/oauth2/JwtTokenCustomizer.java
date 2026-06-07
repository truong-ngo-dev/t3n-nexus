package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.infrastructure.security.key.RsaKeyPairRepository;
// TODO [business]: import vn.t3nexus.oauth2.infrastructure.api.http.internal.SomeServiceClient;
// TODO [business]: import vn.t3nexus.oauth2.infrastructure.security.service.AdminUserDetails;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Single OAuth2TokenCustomizer cho toàn bộ JWT customization:
 * - kid header — xác định RSA key pair dùng để ký
 * - ACCESS_TOKEN: sid (session id), roles
 * - TODO [business]: contexts claim — gọi internal service để lấy context per user
 * - ID_TOKEN: copy third-party claims từ OidcUser
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final Set<String> ID_TOKEN_CLAIMS = Set.of(
            IdTokenClaimNames.ISS, IdTokenClaimNames.SUB, IdTokenClaimNames.AUD,
            IdTokenClaimNames.EXP, IdTokenClaimNames.IAT, IdTokenClaimNames.AUTH_TIME,
            IdTokenClaimNames.NONCE, IdTokenClaimNames.ACR, IdTokenClaimNames.AMR,
            IdTokenClaimNames.AZP, IdTokenClaimNames.AT_HASH, IdTokenClaimNames.C_HASH);

    private final RsaKeyPairRepository keyPairRepository;

    @Override
    public void customize(JwtEncodingContext context) {
        // kid header — luôn set để JWT consumer biết dùng key nào để verify
        String kid = keyPairRepository.findKeyPairs().getFirst().id();
        context.getJwsHeader().keyId(kid);

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            if (context.getAuthorization() != null) {
                // oss_id = OAuthSession.id (ULID) — used by web-gateway for session mapping
                String ossId = context.getAuthorization().getAttribute(SessionEstablishingAuthorizationService.ATTR_OAUTH_SESSION_ID);
                if (ossId != null) {
                    context.getClaims().claim("oss_id", ossId);
                }
            }

            // roles từ granted authorities
            List<String> roles = context.getPrincipal().getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            context.getClaims().claim("roles", roles);
        }

        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            // oss_id in ID token — needed by web-gateway logout handler to clean up Redis mapping
            if (context.getAuthorization() != null) {
                String ossId = context.getAuthorization().getAttribute(SessionEstablishingAuthorizationService.ATTR_OAUTH_SESSION_ID);
                if (ossId != null) {
                    context.getClaims().claim("oss_id", ossId);
                }
            }

            // Copy third-party claims từ OidcUser vào ID token
            Map<String, Object> thirdPartyClaims = extractClaims(context.getPrincipal());
            context.getClaims().claims(existingClaims -> {
                existingClaims.keySet().forEach(thirdPartyClaims::remove);
                ID_TOKEN_CLAIMS.forEach(thirdPartyClaims::remove);
                existingClaims.putAll(thirdPartyClaims);
            });
        }
    }

    private Map<String, Object> extractClaims(Authentication principal) {
        if (principal.getPrincipal() instanceof OidcUser oidcUser) {
            OidcIdToken idToken = oidcUser.getIdToken();
            return new HashMap<>(idToken.getClaims());
        } else if (principal.getPrincipal() instanceof OAuth2User oauth2User) {
            return new HashMap<>(oauth2User.getAttributes());
        }
        return Collections.emptyMap();
    }
}
