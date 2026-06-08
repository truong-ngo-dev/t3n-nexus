package vn.t3nexus.oauth2.infrastructure.security.mfa;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.t3nexus.oauth2.infrastructure.security.service.UserCredentialDetails;

import java.io.IOException;

/**
 * Safety net for the Authorization Server endpoints.
 * Handles the edge case where a user with mfa_enabled=true reaches AS endpoints
 * without completing OTT factor (e.g. session resumed mid-flow).
 * Placed before AuthorizationFilter in the AS filter chain.
 *
 * Normal flow: DeviceAwareAuthenticationSuccessHandler redirects to /mfa after password auth.
 * This filter catches any bypasses.
 */
public class MfaEnforcementFilter extends OncePerRequestFilter {

    private static final String FACTOR_OTT = "FACTOR_OTT";

    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (needsMfa(auth)) {
            requestCache.saveRequest(request, response);
            response.sendRedirect(request.getContextPath() + "/mfa");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean needsMfa(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;

        boolean mfaEnabled;
        if (auth.getPrincipal() instanceof UserCredentialDetails userDetails) {
            mfaEnabled = userDetails.isMfaEnabled();
        } else if (auth.getPrincipal() instanceof OidcUser oidcUser) {
            mfaEnabled = Boolean.TRUE.equals(oidcUser.getClaim("app_mfa_enabled"));
        } else {
            return false;
        }

        if (!mfaEnabled) return false;
        return auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals(FACTOR_OTT));
    }
}
