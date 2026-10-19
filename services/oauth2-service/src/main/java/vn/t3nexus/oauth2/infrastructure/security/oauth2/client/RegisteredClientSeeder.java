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

import java.util.List;
import java.util.UUID;

/**
 * Đưa các client đã seed bằng migration về đúng trạng thái an toàn mỗi lần khởi động (KT-29, KT-30, ADR-0005): bí mật nằm ngoài
 * mã nguồn (biến môi trường) và được băm bằng bộ mã hóa mật khẩu của dịch vụ, vì migration V4/V10 chỉ ghi {@code {noop}...} mà bộ mã
 * hóa BCrypt không hiểu; client trình duyệt bắt buộc PKCE. Ở profile {@code dev} có giá trị mặc định và thêm client {@code postman-dev}
 * (công khai, PKCE) để thử bằng Postman. Khác dev mà thiếu bí mật của storefront-web-bff thì dịch vụ không khởi động.
 */
@Slf4j
@Component
public class RegisteredClientSeeder implements ApplicationRunner {

    static final String STOREFRONT_WEB      = "storefront-web";
    static final String POSTMAN_DEV      = "postman-dev";
    static final String POSTMAN_REDIRECT = "https://oauth.pstmn.io/v1/callback";

    private final RegisteredClientRepository repository;
    private final PasswordEncoder            passwordEncoder;
    private final Environment                environment;
    private final String                     storefrontWebBffSecret;
    private final boolean                    storefrontWebBffRequiresPkce;
    private final List<String>               storefrontWebRedirectUris;
    private final List<String>               storefrontWebPostLogoutUris;
    private final String                     internalClientId;
    private final String                     internalClientSecret;

    public RegisteredClientSeeder(RegisteredClientRepository repository, PasswordEncoder passwordEncoder,
                                  Environment environment,
                                  @Value("${app.clients.storefront-web.secret:}") String storefrontWebBffSecret,
                                  @Value("${app.clients.storefront-web.require-proof-key:true}") boolean storefrontWebBffRequiresPkce,
                                  @Value("${app.clients.storefront-web.redirect-uris:}") List<String> storefrontWebRedirectUris,
                                  @Value("${app.clients.storefront-web.post-logout-redirect-uris:}") List<String> storefrontWebPostLogoutUris,
                                  @Value("${app.internal-client.client-id}") String internalClientId,
                                  @Value("${app.internal-client.client-secret}") String internalClientSecret) {
        this.repository           = repository;
        this.passwordEncoder      = passwordEncoder;
        this.environment          = environment;
        this.storefrontWebBffSecret     = storefrontWebBffSecret;
        this.storefrontWebBffRequiresPkce = storefrontWebBffRequiresPkce;
        this.storefrontWebRedirectUris = clean(storefrontWebRedirectUris);
        this.storefrontWebPostLogoutUris = clean(storefrontWebPostLogoutUris);
        this.internalClientId     = internalClientId;
        this.internalClientSecret = internalClientSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        if (!StringUtils.hasText(storefrontWebBffSecret) && !dev) {
            throw new IllegalStateException("app.clients.storefront-web.secret (STOREFRONT_WEB_CLIENT_SECRET) is required outside the dev profile");
        }
        syncStorefrontWebBff();
        syncInternal();
        if (dev) ensurePostmanDev();
    }

    private void syncStorefrontWebBff() {
        RegisteredClient existing = repository.findByClientId(STOREFRONT_WEB);
        if (existing == null) return;
        RegisteredClient.Builder builder = RegisteredClient.from(existing)
                .clientSettings(ClientSettings.withSettings(existing.getClientSettings().getSettings())
                        .requireProofKey(storefrontWebBffRequiresPkce).build());
        if (StringUtils.hasText(storefrontWebBffSecret)) builder.clientSecret(passwordEncoder.encode(storefrontWebBffSecret));
        // Đặt lại đúng danh sách URI theo cấu hình (host shop, ADR-0006): URI cũ trong migration V4, V9 bị gỡ, không còn chấp nhận.
        if (!storefrontWebRedirectUris.isEmpty()) {
            builder.redirectUris(uris -> { uris.clear(); uris.addAll(storefrontWebRedirectUris); });
        }
        if (!storefrontWebPostLogoutUris.isEmpty()) {
            builder.postLogoutRedirectUris(uris -> { uris.clear(); uris.addAll(storefrontWebPostLogoutUris); });
        }
        repository.save(builder.build());
        log.info("registered client {} synced (PKCE required: {}, redirect URIs: {}, post-logout URIs: {})", STOREFRONT_WEB,
                storefrontWebBffRequiresPkce, storefrontWebRedirectUris, storefrontWebPostLogoutUris);
    }

    private static List<String> clean(List<String> uris) {
        return uris == null ? List.of() : uris.stream().map(String::strip).filter(StringUtils::hasText).toList();
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
