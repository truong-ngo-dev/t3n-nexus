package vn.t3nexus.oauth2.application.user_credential.publish_login_failed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.user_credential.LoginFailedEvent;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishLoginFailed {

    private final UserCredentialRepository userCredentialRepository;
    private final OutboxEventStore         outboxEventStore;

    @Transactional
    public void publish(
            String username,
            String result,
            String deviceHash,
            String acceptLanguage,
            String ipAddress,
            String userAgent,
            String provider) {
        userCredentialRepository.findByEmail(username).ifPresentOrElse(
                credential -> {
                    outboxEventStore.store(new LoginFailedEvent(
                            credential.getId().getValue(),
                            username,
                            result,
                            deviceHash     != null ? deviceHash     : "",
                            acceptLanguage != null ? acceptLanguage : "",
                            ipAddress      != null ? ipAddress      : "",
                            userAgent      != null ? userAgent      : "",
                            provider
                    ));
                    log.debug("[PublishLoginFailed] userId={}, result={}", credential.getId().getValue(), result);
                },
                () -> log.warn("[PublishLoginFailed] user not found for email={}, skipping", username)
        );
    }
}
