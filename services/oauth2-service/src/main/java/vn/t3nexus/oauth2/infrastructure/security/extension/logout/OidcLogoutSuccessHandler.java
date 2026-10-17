package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.JwtTokenCustomizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý đăng xuất OIDC (RP-initiated logout) sau khi Spring Authorization Server đã kiểm yêu cầu (hint tra được và thuộc client, trang
 * đích đã đăng ký). Làm bốn việc theo thứ tự, khớp {@code feature 03 flow.md} mục 3:
 * <ol>
 *   <li>Kết thúc phiên của lần đăng nhập này ({@link LoginSessionLogout}: một giao dịch xóa phiên và các bản ghi ủy quyền). Chỉ khi có
 *       cookie đăng nhập và hint thuộc phiên của cookie đó; không thì không kết thúc gì và vẫn là thành công (INV-ATH-05).</li>
 *   <li>Chỉ khi vừa kết thúc phiên: hủy session Spring (cookie đăng nhập hết tác dụng; {@code DEVICE_KEY} giữ nguyên).</li>
 *   <li>Báo các ứng dụng xóa mã ({@link EndedSessionNotifier}), sau commit, cố gắng hết sức.</li>
 *   <li>Chuyển hướng về trang đích đã đăng ký kèm {@code state}, không có thì về {@code /}.</li>
 * </ol>
 * Lỗi giao dịch ở bước 1 ném ra ngoài: giao dịch đã quay lui, session Spring còn nguyên để người dùng thử lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcLogoutSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginSessionLogout   loginSessionLogout;
    private final EndedSessionNotifier notifier;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcLogoutAuthenticationToken logout = (OidcLogoutAuthenticationToken) authentication;

        endTheSession(request, response, logout);
        redirect(request, response, logout);
    }

    private void endTheSession(HttpServletRequest request, HttpServletResponse response, OidcLogoutAuthenticationToken logout) {
        if (!logout.isPrincipalAuthenticated() || !StringUtils.hasText(logout.getSessionId())) return;   // không cookie đăng nhập

        String hintSid = logout.getIdToken().getClaimAsString(JwtTokenCustomizer.CLAIM_SID);
        EndLoginSession.Result result = loginSessionLogout.end(logout.getSessionId(), hintSid);
        if (!result.ended()) return;

        new SecurityContextLogoutHandler().logout(request, response, (Authentication) logout.getPrincipal());
        try {
            notifier.notifyEnded(result.endedClientSessions());
        } catch (RuntimeException e) {
            log.warn("could not tell the applications that a login session ended: {}", e.getClass().getSimpleName());
        }
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, OidcLogoutAuthenticationToken logout)
            throws IOException {
        if (logout.isAuthenticated() && StringUtils.hasText(logout.getPostLogoutRedirectUri())) {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(logout.getPostLogoutRedirectUri());
            if (StringUtils.hasText(logout.getState())) {
                uriBuilder.queryParam(OAuth2ParameterNames.STATE, UriUtils.encode(logout.getState(), StandardCharsets.UTF_8));
            }
            new DefaultRedirectStrategy().sendRedirect(request, response, uriBuilder.build(true).toUriString());
        } else {
            new DefaultRedirectStrategy().sendRedirect(request, response, "/");
        }
    }
}
