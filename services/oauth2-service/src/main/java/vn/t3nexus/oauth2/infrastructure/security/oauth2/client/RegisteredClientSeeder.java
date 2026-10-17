package vn.t3nexus.oauth2.infrastructure.security.oauth2.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Đưa các client đã seed bằng migration về đúng trạng thái an toàn mỗi lần khởi động (KT-29, KT-30, ADR-0005): bí mật nằm ngoài
 * mã nguồn (biến môi trường) và được băm bằng bộ mã hóa mật khẩu của dịch vụ, vì migration V4/V10 chỉ ghi {@code {noop}...} mà bộ mã
 * hóa BCrypt không hiểu; client trình duyệt bắt buộc PKCE. Ở profile {@code dev} có giá trị mặc định và thêm client {@code postman-dev}
 * (công khai, PKCE) để thử bằng Postman. Khác dev mà thiếu bí mật của web-gateway thì dịch vụ không khởi động.
 */
@Slf4j
@Component
public class RegisteredClientSeeder implements ApplicationRunner {

    static final String WEB_GATEWAY      = "web-gateway";
    static final String POSTMAN_DEV      = "postman-dev";
    static final String POSTMAN_REDIRECT = "https://oauth.pstmn.io/v1/callback";

    private final RegisteredClientRepository repository;
    private final PasswordEncoder            passwordEncoder;
    private final Environment                environment;
    private final String                     webGatewaySecret;
    private final boolean                    webGatewayRequiresPkce;
    private final String                     internalClientId;
    private final String                     internalClientSecret;

    public RegisteredClientSeeder(RegisteredClientRepository repository, PasswordEncoder passwordEncoder,
                                  Environment environment,
                                  @Value("${app.clients.web-gateway.secret:}") String webGatewaySecret,
                                  @Value("${app.clients.web-gateway.require-proof-key:false}") boolean webGatewayRequiresPkce,
                                  @Value("${app.internal-client.client-id}") String internalClientId,
                                  @Value("${app.internal-client.client-secret}") String internalClientSecret) {
        this.repository           = repository;
        this.passwordEncoder      = passwordEncoder;
        this.environment          = environment;
        this.webGatewaySecret     = webGatewaySecret;
        this.webGatewayRequiresPkce = webGatewayRequiresPkce;
        this.internalClientId     = internalClientId;
        this.internalClientSecret = internalClientSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        if (!StringUtils.hasText(webGatewaySecret) && !dev) {
            throw new IllegalStateException("app.clients.web-gateway.secret (WEB_GATEWAY_CLIENT_SECRET) is required outside the dev profile");
        }
        syncWebGateway();
        syncInternal();
        if (dev) ensurePostmanDev();
    }

    private void syncWebGateway() {
        RegisteredClient existing = repository.findByClientId(WEB_GATEWAY);
        if (existing == null) return;
        RegisteredClient.Builder builder = RegisteredClient.from(existing)
                .clientSettings(ClientSettings.withSettings(existing.getClientSettings().getSettings())
                        .requireProofKey(webGatewayRequiresPkce).build());
        if (StringUtils.hasText(webGatewaySecret)) builder.clientSecret(passwordEncoder.encode(webGatewaySecret));
        repository.save(builder.build());
        log.info("registered client {} synced (PKCE required: {})", WEB_GATEWAY, webGatewayRequiresPkce);
    }

    private void syncInternal() {
        RegisteredClient existing = repository.findByClientId(internalClientId);
        if (existing == null) return;
        repository.save(RegisteredClient.from(existing).clientSecret(passwordEncoder.encode(internalClientSecret)).build());
    }

    private void ensurePostmanDev() {
        if (repository.findByClientId(POSTMAN_DEV) != null) return;
        repository.save(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(POSTMAN_DEV)
                .clientName("Postman (dev)")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(POSTMAN_REDIRECT)
                .postLogoutRedirectUri(POSTMAN_REDIRECT)
                .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .build());
        log.info("dev client {} created", POSTMAN_DEV);
    }
}
