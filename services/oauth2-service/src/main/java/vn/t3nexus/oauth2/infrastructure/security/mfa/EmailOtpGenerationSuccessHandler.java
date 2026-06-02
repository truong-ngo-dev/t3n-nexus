package vn.t3nexus.oauth2.infrastructure.security.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public void handle(HttpServletRequest request, HttpServletResponse response,
            OneTimeToken oneTimeToken) throws IOException {
        UserCredentialDetails principal = (UserCredentialDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        sendLoginOtp.handle(new SendLoginOtp.Command(
                principal.getUserId(),
                principal.getEmail(),
                oneTimeToken.getTokenValue()));
        response.sendRedirect(request.getContextPath() + "/mfa/verify");
    }
}
