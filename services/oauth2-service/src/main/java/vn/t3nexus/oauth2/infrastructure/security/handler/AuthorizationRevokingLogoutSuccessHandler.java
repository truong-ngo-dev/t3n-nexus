package vn.t3nexus.oauth2.infrastructure.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import vn.t3nexus.oauth2.application.session.revoke_session.RevokeSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RequiredArgsConstructor
public class AuthorizationRevokingLogoutSuccessHandler implements LogoutSuccessHandler {

    private final RevokeSession    revokeSession;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    @SuppressWarnings("all")
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication instanceof OidcLogoutAuthenticationToken oidcLogout)) {
            return;
        }

        if (oidcLogout.isAuthenticated() && StringUtils.hasText(oidcLogout.getPostLogoutRedirectUri())) {
            revokeSessionFromToken(oidcLogout);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(oidcLogout.getPostLogoutRedirectUri());
            if (StringUtils.hasText(oidcLogout.getState())) {
                uriBuilder.queryParam(OAuth2ParameterNames.STATE, UriUtils.encode(oidcLogout.getState(), StandardCharsets.UTF_8));
            }
            redirectStrategy.sendRedirect(request, response, uriBuilder.build(true).toUriString());
        } else {
            redirectStrategy.sendRedirect(request, response, "/");
        }
    }

    private void revokeSessionFromToken(OidcLogoutAuthenticationToken oidcLogout) {
        OidcIdToken idToken = oidcLogout.getIdToken();
        if (idToken == null) return;

        String ossId = idToken.getClaimAsString("oss_id");
        if (!StringUtils.hasText(ossId)) {
            log.warn("[Logout] oss_id claim missing from id_token — OAuthSession revocation skipped");
            return;
        }

        revokeSession.handle(new RevokeSession.Command(ossId));
    }
}
