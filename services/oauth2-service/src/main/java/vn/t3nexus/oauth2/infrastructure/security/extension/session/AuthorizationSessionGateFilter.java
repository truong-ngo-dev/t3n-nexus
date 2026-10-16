package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.infrastructure.security.session.LoginSessionAttributes;

import java.io.IOException;

/**
 * Cửa kiểm hạn ở {@code /oauth2/authorize} (ADR-0003 mục 2): chạy {@link ResolveAuthorizationSession} cho yêu cầu cấp mã của
 * người đã xác thực rồi để kết quả trong thuộc tính của yêu cầu ({@link RequestAttributeAuthorizationSessionSource}) cho lớp bọc
 * {@code OAuth2AuthorizationService} đọc lúc Spring lưu mã.
 *
 * <p>Phiên của ta không còn hợp lệ (cookie Spring còn sống nhưng phiên đã xóa hoặc hết hạn, tài khoản bị khóa, hoặc người dùng
 * xác thực bằng đường không mở phiên của ta) thì <b>hủy xác thực và session HTTP</b> rồi cho yêu cầu đi tiếp: Spring thấy chưa
 * xác thực và chuyển về trang đăng nhập (UX đúng hơn là trả lỗi). Lỗi cơ sở dữ liệu không bị nuốt: yêu cầu thất bại, không cấp
 * mã khi không đọc được phiên. Yêu cầu chưa xác thực đi qua nguyên vẹn. Phải đứng trước {@code AuthorizationFilter}.
 */
@Slf4j
public class AuthorizationSessionGateFilter extends OncePerRequestFilter {

    public static final String AUTHORIZE_PATH = "/oauth2/authorize";

    private final ResolveAuthorizationSession resolveAuthorizationSession;
    private final RegisteredClientRepository  registeredClients;

    public AuthorizationSessionGateFilter(ResolveAuthorizationSession resolveAuthorizationSession,
                                          RegisteredClientRepository registeredClients) {
        this.resolveAuthorizationSession = resolveAuthorizationSession;
        this.registeredClients           = registeredClients;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTHORIZE_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        RegisteredClient client = clientId == null ? null : registeredClients.findByClientId(clientId);
        if (client == null) {
            chain.doFilter(request, response);      // let spring handle unknown client error
            return;
        }

        HttpSession session = request.getSession(false);
        Object loginSessionId = session == null ? null : session.getAttribute(LoginSessionAttributes.LOGIN_SESSION_ID);
        if (loginSessionId instanceof String id) {
            try {
                ResolveAuthorizationSession.Result result = resolveAuthorizationSession.handle(new ResolveAuthorizationSession.Command(id, session.getId(), client.getId()));
                request.setAttribute(RequestAttributeAuthorizationSessionSource.ATTRIBUTE, result);
                chain.doFilter(request, response);
                return;
            } catch (LoginSessionException | UserAccountException | IllegalArgumentException invalid) {
                log.info("login session is no longer valid at authorize: {}", invalid.getClass().getSimpleName());
            }
        }
        SecurityContextHolder.clearContext();
        if (session != null) session.invalidate();
        chain.doFilter(request, response);
    }
}
