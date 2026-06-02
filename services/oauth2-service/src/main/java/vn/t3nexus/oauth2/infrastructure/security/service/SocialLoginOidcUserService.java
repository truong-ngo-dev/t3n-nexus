package vn.t3nexus.oauth2.infrastructure.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
// TODO [business]: import vn.t3nexus.oauth2.domain.user_credential.SocialIdentity;
// TODO [business]: import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles social login via OIDC providers (e.g. Google).
 * Sau khi external provider xác thực user:
 * 1. TODO [business]: Gọi domain service để find-or-create user credential từ social identity.
 * 2. Override OIDC principal `sub` với userId của hệ thống.
 * 3. TODO [business]: Thêm requires_profile_completion vào claims nếu cần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String CLAIM_REQUIRES_PROFILE_COMPLETION = "requires_profile_completion";

    // TODO [business]: private final UserCredentialRepository userCredentialRepository;
    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser googleUser = delegate.loadUser(userRequest);

        String provider       = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        String providerUserId = googleUser.getSubject();
        String providerEmail  = googleUser.getEmail();

        /* TODO [business]: resolve/create user từ social identity
        User user = resolveSocialUser(provider, providerUserId, providerEmail);
        OidcIdToken enrichedToken = buildEnrichedToken(googleUser, user.getId().getValueAsString(), user.isRequiresProfileCompletion());
        return new DefaultOidcUser(googleUser.getAuthorities(), enrichedToken, googleUser.getUserInfo());
        */

        // Placeholder — trả về user gốc từ provider (chưa có system userId)
        log.warn("[SocialLogin] Business logic chưa implement — trả về Google user gốc cho provider={}, sub={}", provider, providerUserId);
        return googleUser;
    }

    /* TODO [business]: implement resolveSocialUser
    private User resolveSocialUser(String provider, String providerUserId, String providerEmail) {
        try {
            return userCredentialRepository.findBySocialIdentity(new SocialIdentity(provider, providerUserId, providerEmail))
                    .orElseGet(() -> createSocialUser(provider, providerUserId, providerEmail));
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SocialLogin] Failed to resolve social user: {}", e.getMessage());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("internal_error"), "Failed to resolve social user: " + e.getMessage());
        }
    }
    */

    private static OidcIdToken buildEnrichedToken(OidcUser googleUser, String systemUserId, boolean requiresProfileCompletion) {
        Map<String, Object> claims = new HashMap<>(googleUser.getIdToken().getClaims());
        claims.put("sub", systemUserId);
        claims.put(CLAIM_REQUIRES_PROFILE_COMPLETION, requiresProfileCompletion);
        return new OidcIdToken(
                googleUser.getIdToken().getTokenValue(),
                googleUser.getIdToken().getIssuedAt(),
                googleUser.getIdToken().getExpiresAt(),
                claims
        );
    }
}
