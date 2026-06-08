package vn.t3nexus.oauth2.infrastructure.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import vn.t3nexus.oauth2.application.user_credential.resolve_social_user.ResolveSocialUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles social login via OIDC providers (e.g. Google).
 * Lookup by email — username = email, so email uniquely identifies an account.
 * Account linking: if email already exists (credential user), link silently (provider verified ownership).
 * New user: registerWithOAuth → UserRegisteredEvent(method=OAUTH) → notification-service sends "set password" email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final ResolveSocialUser resolveSocialUser;
    private final OidcUserService   delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser providerUser = delegate.loadUser(userRequest);

        String email    = providerUser.getEmail();
        String fullName = providerUser.getFullName();

        ResolveSocialUser.Result result;
        try {
            result = resolveSocialUser.handle(new ResolveSocialUser.Command(email, fullName));
        } catch (Exception e) {
            log.error("[SocialLogin] Failed to resolve social user email={}: {}", email, e.getMessage());
            throw new OAuth2AuthenticationException(new OAuth2Error("internal_error"), e.getMessage(), e);
        }

        if (result.locked()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_locked", "Account is locked", null));
        }

        Instant issuedAt = providerUser.getIdToken().getIssuedAt() != null
                ? providerUser.getIdToken().getIssuedAt()
                : Instant.now();
        Collection<GrantedAuthority> enrichedAuthorities = new ArrayList<>(providerUser.getAuthorities());
        enrichedAuthorities.add(
                FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY)
                        .issuedAt(issuedAt)
                        .build()
        );
        OidcIdToken enrichedToken = buildEnrichedToken(providerUser, result.userId(), result.newAccount());
        return new DefaultOidcUser(enrichedAuthorities, enrichedToken, providerUser.getUserInfo());
    }

    private static OidcIdToken buildEnrichedToken(OidcUser providerUser, String systemUserId, boolean newAccount) {
        Map<String, Object> claims = new HashMap<>(providerUser.getIdToken().getClaims());
        claims.put("sub", systemUserId);
        if (newAccount) {
            claims.put("is_new_account", true);
        }
        return new OidcIdToken(
                providerUser.getIdToken().getTokenValue(),
                providerUser.getIdToken().getIssuedAt(),
                providerUser.getIdToken().getExpiresAt(),
                claims
        );
    }
}
