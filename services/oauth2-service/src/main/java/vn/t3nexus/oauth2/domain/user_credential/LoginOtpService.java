package vn.t3nexus.oauth2.domain.user_credential;

import org.springframework.stereotype.Component;

@Component
public class LoginOtpService {

    public LoginOtpRequestedEvent requestOtp(String userId, String email, String token) {
        return new LoginOtpRequestedEvent(userId, email, token);
    }
}
