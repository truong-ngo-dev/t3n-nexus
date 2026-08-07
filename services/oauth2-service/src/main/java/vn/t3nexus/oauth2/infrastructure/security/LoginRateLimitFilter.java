package vn.t3nexus.oauth2.infrastructure.security;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.t3nexus.lib.ratelimiter.RateLimiter;

import java.io.IOException;
import java.time.Duration;

/**
 * Brute-force guard cho LOCAL password login — chặn đúng POST /login, trước khi request chạm
 * UsernamePasswordAuthenticationFilter (tức trước cả BCrypt compare).
 *
 * Cố ý KHÔNG đặt trong OAuth2UserDetailsService.loadUserByUsername() — method đó bị OTT
 * authentication provider gọi lại sau khi verify OTP đúng (xem login-impl.md mục 2), nên rate-limit
 * ở đó sẽ tính trùng quota cho 1 lần login MFA và áp nhầm lên bất kỳ caller nào khác trong tương lai.
 * Filter riêng ở đây chỉ scope đúng vào request POST /login, không đụng gì tới lookup logic.
 */
@Slf4j
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int      LIMIT  = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain chain) throws ServletException, IOException {

        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath())) {
            String username = request.getParameter("username");
            if (StringUtils.hasText(username)
                    && !rateLimiter.tryAcquire("login_attempt:" + username, LIMIT, WINDOW)) {
                log.warn("[LoginRateLimitFilter] Rate limit exceeded for username='{}'", username);
                response.sendRedirect(request.getContextPath() + "/login?locked");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
