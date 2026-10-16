package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import vn.t3nexus.oauth2.application.session.end_idp_session.EndIdpSession;
import vn.t3nexus.oauth2.infrastructure.adapter.http.WebGatewayRevocationClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý đăng xuất OIDC (RP-initiated logout) sau khi Spring Authorization Server đã kiểm yêu cầu: đăng xuất khỏi Spring
 * Security, kết thúc phiên của ta rồi chuyển hướng về {@code post_logout_redirect_uri}. Spring chỉ định nghĩa sự kiện, việc
 * làm tiếp là của ta, nên đây là phần mở rộng (cắm logic vào chuỗi), không phải cấu hình của khái niệm OAuth2.
 *
 * <p>Còn theo mô hình phiên cũ ({@code EndIdpSession}, {@code WebGatewayRevocationClient}); sẽ viết lại ở feature đăng xuất.
 */
@Component
@RequiredArgsConstructor
public class OidcLogoutSuccessHandler implements AuthenticationSuccessHandler {

    private final EndIdpSession endIdpSession;
    private final WebGatewayRevocationClient revocationClient;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcLogoutAuthenticationToken oidcLogoutAuthentication = (OidcLogoutAuthenticationToken) authentication;

        if (oidcLogoutAuthentication.isPrincipalAuthenticated()
                && StringUtils.hasText(oidcLogoutAuthentication.getSessionId())) {
            new SecurityContextLogoutHandler().logout(
                    request, response, (Authentication) oidcLogoutAuthentication.getPrincipal());

            // JdbcIndexedSessionRepository không publish SessionDeletedEvent (Spring Session JDBC 4.x removed event support)
            String idpSessionId = oidcLogoutAuthentication.getSessionId();
            EndIdpSession.Result result = endIdpSession.handle(new EndIdpSession.Command(idpSessionId));
            result.ossIds().forEach(revocationClient::revoke);
        }

        if (oidcLogoutAuthentication.isAuthenticated()
                && StringUtils.hasText(oidcLogoutAuthentication.getPostLogoutRedirectUri())) {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString(oidcLogoutAuthentication.getPostLogoutRedirectUri());
            if (StringUtils.hasText(oidcLogoutAuthentication.getState())) {
                uriBuilder.queryParam(OAuth2ParameterNames.STATE,
                        UriUtils.encode(oidcLogoutAuthentication.getState(), StandardCharsets.UTF_8));
            }
            new DefaultRedirectStrategy().sendRedirect(request, response, uriBuilder.build(true).toUriString());
        } else {
            new DefaultRedirectStrategy().sendRedirect(request, response, "/");
        }
    }
}
