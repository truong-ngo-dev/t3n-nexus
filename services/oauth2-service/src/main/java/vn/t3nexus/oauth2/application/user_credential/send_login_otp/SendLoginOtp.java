package vn.t3nexus.oauth2.application.user_credential.send_login_otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.user_credential.LoginOtpRequestedEvent;
import vn.t3nexus.oauth2.domain.user_credential.LoginOtpService;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendLoginOtp implements CommandHandler<SendLoginOtp.Command, Void> {

    public record Command(String userId, String email, String token) {}

    private final LoginOtpService loginOtpService;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public Void handle(Command command) {
        LoginOtpRequestedEvent event = loginOtpService.requestOtp(command.userId(), command.email(), command.token());
        eventDispatcher.dispatch(event);
        log.debug("[SendLoginOtp] OTP event dispatched for userId={}", command.userId());
        return null;
    }
}
