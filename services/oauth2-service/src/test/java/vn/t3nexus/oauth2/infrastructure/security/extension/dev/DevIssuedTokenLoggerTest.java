package vn.t3nexus.oauth2.infrastructure.security.extension.dev;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.IssuedTokenListener;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.SessionBinding;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ghi token thật ra log ở dev: dòng log có đủ token, tài khoản, client, {@code sid}; bean chỉ tồn tại khi cả profile {@code dev} lẫn cờ
 * {@code observability.logging.reveal-secrets=true} đều đúng.
 */
class DevIssuedTokenLoggerTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private static final String SID = "01938a5e-1c00-7000-8000-0000000000a1";

    private final Logger logger = (Logger) LoggerFactory.getLogger(DevIssuedTokenLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private static OAuth2Authorization issued() {
        RegisteredClient client = RegisteredClient.withId("rc-1").clientId("web-gateway").clientSecret("{noop}x")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/cb").build();
        SessionBinding binding = new SessionBinding("login-1", SID, NOW.plusSeconds(3600), Role.USER);
        return OAuth2Authorization.withRegisteredClient(client).id("auth-1").principalName("user-1")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attributes(a -> a.putAll(binding.toAttributes()))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "ACCESS.TOKEN.VALUE", NOW, NOW.plusSeconds(900)))
                .refreshToken(new OAuth2RefreshToken("REFRESH-TOKEN-VALUE", NOW, NOW.plusSeconds(3600)))
                .build();
    }

    private static RegisteredClientRepository clientsKnowing(String id, String clientId) {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        when(repository.findById(id)).thenReturn(RegisteredClient.withId(id).clientId(clientId).clientSecret("{noop}x")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/cb").build());
        return repository;
    }

    @Test
    void the_line_carries_the_tokens_the_account_the_client_and_the_sid() {
        new DevIssuedTokenLogger(clientsKnowing("rc-1", "web-gateway")).onTokenIssued(issued());

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("sub=user-1", "client=web-gateway", "sid=" + SID,
                    "access_token=ACCESS.TOKEN.VALUE", "refresh_token=REFRESH-TOKEN-VALUE", "id_token=-");
        });
    }

    @Test
    void an_unknown_client_falls_back_to_the_registered_id_instead_of_failing() {
        new DevIssuedTokenLogger(mock(RegisteredClientRepository.class)).onTokenIssued(issued());

        assertThat(appender.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("client=rc-1"));
    }

    // ───────────── điều kiện tồn tại của bean ─────────────

    private ApplicationContextRunner runner(String... properties) {
        return new ApplicationContextRunner()
                .withUserConfiguration(DevIssuedTokenLogger.class)       // đăng ký chính lớp: điều kiện đặt trên lớp mới được áp dụng
                .withBean(RegisteredClientRepository.class, () -> mock(RegisteredClientRepository.class))
                .withPropertyValues(properties);
    }

    @Test
    void the_logger_exists_when_the_dev_profile_and_the_reveal_flag_are_both_on() {
        runner("spring.profiles.active=dev", "observability.logging.reveal-secrets=true")
                .run(context -> assertThat(context).hasSingleBean(IssuedTokenListener.class));
    }

    @Test
    void without_the_flag_or_without_the_dev_profile_nothing_is_created() {
        runner("spring.profiles.active=dev").run(context -> assertThat(context).doesNotHaveBean(IssuedTokenListener.class));
        runner("observability.logging.reveal-secrets=true").run(context -> assertThat(context).doesNotHaveBean(IssuedTokenListener.class));
        runner("spring.profiles.active=prod", "observability.logging.reveal-secrets=true")
                .run(context -> assertThat(context).doesNotHaveBean(IssuedTokenListener.class));
        runner().run(context -> assertThat(context).doesNotHaveBean(IssuedTokenListener.class));
    }
}
