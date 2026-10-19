package vn.t3nexus.oauth2.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Điểm vào "chưa xác thực" dùng chung cho hai chuỗi lọc có đăng nhập (máy chủ ủy quyền và chuỗi mặc định), theo bảng hành vi lỗi
 * (`service.md` mục 3.1):
 * <ul>
 *   <li><b>Endpoint giao thức luôn 401</b>, không bao giờ chuyển hướng: {@code /userinfo} kèm {@code WWW-Authenticate: Bearer} (RFC 6750); điểm cấp token,
 *       thu hồi, kiểm tra kèm {@code WWW-Authenticate: Basic} và thân {@code invalid_client} (RFC 6749 mục 5.2).</li>
 *   <li><b>Điều hướng trình duyệt</b> (chỉ khi {@code Accept} nêu rõ {@code text/html}) thì chuyển tới {@code /login}. {@code *}{@code /*} và thiếu {@code Accept}
 *       không tính là điều hướng: cấu hình mặc định coi chúng là HTML nên client OIDC và công cụ dòng lệnh bị chuyển hướng thay vì nhận 401.</li>
 *   <li>Còn lại 401 (mặc định). Đặt điểm vào tường minh thay cho các điểm vào mà {@code formLogin}, {@code oauth2Login}, {@code oneTimeTokenLogin} tự đăng ký
 *       (đều trỏ {@code /login}).</li>
 * </ul>
 */
public final class AuthenticationEntryPoints {

    private AuthenticationEntryPoints() {
    }

    public static AuthenticationEntryPoint browserAndProtocol() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entries = new LinkedHashMap<>();
        entries.put(PathPatternRequestMatcher.withDefaults().matcher("/userinfo"), new BearerTokenAuthenticationEntryPoint());
        entries.put(new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/oauth2/token"),
                PathPatternRequestMatcher.withDefaults().matcher("/oauth2/revoke"),
                PathPatternRequestMatcher.withDefaults().matcher("/oauth2/introspect")), clientAuthenticationChallenge());
        entries.put(navigation(), new LoginUrlAuthenticationEntryPoint("/login"));
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(entries);
        delegating.setDefaultEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        return delegating;
    }

    /** Yêu cầu của trình duyệt đang điều hướng: {@code Accept} nêu rõ {@code text/html}, {@code *}{@code /*} bị bỏ qua. */
    static RequestMatcher navigation() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }

    private static AuthenticationEntryPoint clientAuthenticationChallenge() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"oauth2\"");
            response.setHeader("Cache-Control", "no-store");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"invalid_client\"}");
        };
    }
}
