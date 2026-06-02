package vn.t3nexus.oauth2.infrastructure.security.mfa;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.Instant;

@Slf4j
@Component
public class EmailOtpOneTimeTokenService implements OneTimeTokenService {

    private static final String OTP_KEY      = "mfa_otp";
    private static final String USERNAME_KEY = "mfa_otp_username";
    private static final String EXPIRY_KEY   = "mfa_otp_expiry";
    private static final int    VALID_SECS   = 300;

    private final SecureRandom rng = new SecureRandom();

    @Override
    public @NotNull OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        HttpSession session  = currentSession(true);
        String      otp      = String.format("%06d", rng.nextInt(1_000_000));
        Instant     expiresAt = Instant.now().plusSeconds(VALID_SECS);

        session.setAttribute(OTP_KEY,      otp);
        session.setAttribute(USERNAME_KEY, request.getUsername());
        session.setAttribute(EXPIRY_KEY,   expiresAt.getEpochSecond());

        log.debug("[OTT] Generated for user={}", request.getUsername());
        return new DefaultOneTimeToken(otp, request.getUsername(), expiresAt);
    }

    /**
     * OTP stays valid across wrong attempts — only cleared on success or expiry.
     * Brute-force protection is handled at the rate-limiter layer.
     */
    @Override
    public OneTimeToken consume(@NotNull OneTimeTokenAuthenticationToken authToken) {
        HttpSession session = currentSession(false);
        if (session == null) throw new BadCredentialsException("Session expired");

        String stored   = (String) session.getAttribute(OTP_KEY);
        String username = (String) session.getAttribute(USERNAME_KEY);
        Long   expiry   = (Long)   session.getAttribute(EXPIRY_KEY);

        if (stored == null || username == null || expiry == null) {
            throw new BadCredentialsException("No active OTP found");
        }
        if (Instant.now().getEpochSecond() > expiry) {
            invalidate(session);
            throw new BadCredentialsException("OTP has expired");
        }
        if (!stored.equals(authToken.getTokenValue())) {
            throw new BadCredentialsException("Invalid OTP");
        }

        invalidate(session);
        log.debug("[OTT] Consumed for user={}", username);
        return new DefaultOneTimeToken(authToken.getTokenValue(), username, Instant.ofEpochSecond(expiry));
    }

    private void invalidate(HttpSession session) {
        session.removeAttribute(OTP_KEY);
        session.removeAttribute(USERNAME_KEY);
        session.removeAttribute(EXPIRY_KEY);
    }

    private HttpSession currentSession(boolean create) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession(create);
    }
}
