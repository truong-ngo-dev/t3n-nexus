package vn.t3nexus.oauth2.infrastructure.security.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.application.user_credential.send_login_otp.SendLoginOtp;
import vn.t3nexus.oauth2.infrastructure.security.service.UserCredentialDetails;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOtpGenerationSuccessHandler implements OneTimeTokenGenerationSuccessHandler {

    private final SendLoginOtp sendLoginOtp;

    @Override
    public void handle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                       @NotNull OneTimeToken oneTimeToken) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String userId;
        String email;
        if (authentication.getPrincipal() instanceof UserCredentialDetails userDetails) {
            userId = userDetails.getUserId();
            email  = userDetails.getEmail();
        } else if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            userId = oidcUser.getSubject();
            email  = oidcUser.getEmail();
        } else {
            throw new IllegalStateException("Unsupported principal type for OTP generation: "
                    + authentication.getPrincipal().getClass().getName());
        }

        sendLoginOtp.handle(new SendLoginOtp.Command(userId, email, oneTimeToken.getTokenValue()));
        response.sendRedirect(request.getContextPath() + "/mfa/verify");
    }
}
