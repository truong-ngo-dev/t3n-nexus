package vn.t3nexus.oauth2.infrastructure.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
// TODO [business]: import vn.t3nexus.oauth2.application.session.revoke.RevokeSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Redirect sau khi logout thành công qua OIDC RP-Initiated Logout.
 * Business hook: revoke OAuthSession (TODO [business]).
 */
@Slf4j
public class AuthorizationRevokingLogoutSuccessHandler implements LogoutSuccessHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    // TODO [business]: private final RevokeSession revokeSession;

    // TODO [business]: constructor nên inject RevokeSession
    public AuthorizationRevokingLogoutSuccessHandler() {
    }

    @Override
    @SuppressWarnings("all")
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication instanceof OidcLogoutAuthenticationToken oidcLogout)) {
            return;
        }

        if (oidcLogout.isAuthenticated() && StringUtils.hasText(oidcLogout.getPostLogoutRedirectUri())) {
            String idToken = Objects.requireNonNull(oidcLogout.getIdToken()).getTokenValue();
            log.info("Processing logout for session: {}", oidcLogout.getSessionId());

            // TODO [business]: revokeSession.handle(new RevokeSession.Command(idToken));

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(oidcLogout.getPostLogoutRedirectUri());
            if (StringUtils.hasText(oidcLogout.getState())) {
                uriBuilder.queryParam(OAuth2ParameterNames.STATE, UriUtils.encode(oidcLogout.getState(), StandardCharsets.UTF_8));
            }
            redirectStrategy.sendRedirect(request, response, uriBuilder.build(true).toUriString());
        } else {
            redirectStrategy.sendRedirect(request, response, "/");
        }
    }
}
