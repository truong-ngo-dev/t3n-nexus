package vn.t3nexus.oauth2.infrastructure.security.oauth2.client;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Đưa các client đã seed bằng migration và cấu hình nhiều BFF (storefront, seller, ops...) về đúng trạng thái an toàn mỗi lần khởi động
 * (KT-29, KT-30, ADR-0005): bí mật nằm ngoài mã nguồn (biến môi trường) và được băm bằng bộ mã hóa mật khẩu của dịch vụ, vì migration V4/V10 chỉ ghi
 * {@code {noop}...} mà bộ mã hóa BCrypt không hiểu; client trình duyệt bắt buộc PKCE. Ở profile {@code dev} có giá trị mặc định và thêm client
 * {@code postman-dev} (công khai, PKCE) để thử bằng Postman. Khác dev mà thiếu bí mật của bất kỳ BFF nào thì dịch vụ không khởi động.
 */
@Slf4j
@Component
public class RegisteredClientSeeder implements ApplicationRunner {

    static final String STOREFRONT_WEB   = "storefront-web";
    static final String POSTMAN_DEV      = "postman-dev";
    static final String POSTMAN_REDIRECT = "https://oauth.pstmn.io/v1/callback";

    private final RegisteredClientRepository repository;
    private final PasswordEncoder            passwordEncoder;
    private final Environment                environment;
    private final BffClientsProperties       bffs;
    private final String                     internalClientId;
    private final String                     internalClientSecret;

    public RegisteredClientSeeder(RegisteredClientRepository repository,
                                  PasswordEncoder passwordEncoder,
                                  Environment environment,
                                  BffClientsProperties bffs,
                                  @Value("${app.internal-client.client-id}") String internalClientId,
                                  @Value("${app.internal-client.client-secret}") String internalClientSecret) {
        this.repository           = repository;
        this.passwordEncoder      = passwordEncoder;
        this.environment          = environment;
        this.bffs                 = bffs;
        this.internalClientId     = internalClientId;
        this.internalClientSecret = internalClientSecret;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        for (Map.Entry<String, BffClientsProperties.Bff> entry : bffs.entrySet()) {
            String name = entry.getKey();
            BffClientsProperties.Bff bff = entry.getValue();
            String secret = bff != null ? bff.getSecret() : null;
            if (!StringUtils.hasText(secret) && !dev) {
                throw new IllegalStateException("app.bffs." + name + ".secret is required outside the dev profile");
            }
        }
        for (Map.Entry<String, BffClientsProperties.Bff> entry : bffs.entrySet()) {
            syncBff(entry.getKey(), entry.getValue());
        }
        syncInternal();
        if (dev) {
            ensurePostmanDev();
        }
    }

    private void syncBff(String name, BffClientsProperties.Bff bff) {
        if (bff == null || !StringUtils.hasText(bff.getClientId())) {
            return;
        }
        RegisteredClient existing = repository.findByClientId(bff.getClientId());
        List<String> redirectUris = clean(bff.getRedirectUris());
        List<String> postLogoutRedirectUris = clean(bff.getPostLogoutRedirectUris());
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));

        if (existing == null) {
            RegisteredClient template = repository.findByClientId(STOREFRONT_WEB);
            if (template == null) {
                log.warn("template client {} not found, cannot seed bff {}", STOREFRONT_WEB, bff.getClientId());
                return;
            }
            String capitalizedName = StringUtils.capitalize(name);
            String clientName = capitalizedName + " Web BFF";
            RegisteredClient.Builder builder = RegisteredClient.from(template)
                    .id(bff.getClientId() + "-client-id")
                    .clientId(bff.getClientId())
                    .clientName(clientName)
                    .clientSettings(ClientSettings.withSettings(template.getClientSettings().getSettings())
                            .requireProofKey(bff.isRequireProofKey()).build());

            builder.redirectUris(uris -> {
                uris.clear();
                uris.addAll(redirectUris);
            });
            builder.postLogoutRedirectUris(uris -> {
                uris.clear();
                uris.addAll(postLogoutRedirectUris);
            });

            String secret = bff.getSecret();
            if (StringUtils.hasText(secret)) {
                builder.clientSecret(passwordEncoder.encode(secret));
            } else if (dev) {
                builder.clientSecret(passwordEncoder.encode("secret"));
            }

            RegisteredClient client = builder.build();
            repository.save(client);
            log.info("registered client {} created (PKCE required: {}, redirect URIs: {}, post-logout URIs: {})",
                    bff.getClientId(), bff.isRequireProofKey(), redirectUris, postLogoutRedirectUris);
        } else {
            RegisteredClient.Builder builder = RegisteredClient.from(existing)
                    .clientSettings(ClientSettings.withSettings(existing.getClientSettings().getSettings())
                            .requireProofKey(bff.isRequireProofKey()).build());
            if (StringUtils.hasText(bff.getSecret())) {
                builder.clientSecret(passwordEncoder.encode(bff.getSecret()));
            }
            if (!redirectUris.isEmpty()) {
                builder.redirectUris(uris -> {
                    uris.clear();
                    uris.addAll(redirectUris);
                });
            }
            if (!postLogoutRedirectUris.isEmpty()) {
                builder.postLogoutRedirectUris(uris -> {
                    uris.clear();
                    uris.addAll(postLogoutRedirectUris);
                });
            }
            repository.save(builder.build());
            log.info("registered client {} synced (PKCE required: {}, redirect URIs: {}, post-logout URIs: {})",
                    bff.getClientId(), bff.isRequireProofKey(), redirectUris, postLogoutRedirectUris);
        }
    }

    private static List<String> clean(List<String> uris) {
        return uris == null ? List.of() : uris.stream().map(String::strip).filter(StringUtils::hasText).toList();
    }

    private void syncInternal() {
        RegisteredClient existing = repository.findByClientId(internalClientId);
        if (existing == null) {
            return;
        }
        Set<String> scopesToAdd = new HashSet<>();
        for (String name : bffs.keySet()) {
            scopesToAdd.add(BffClientsProperties.internalScope(name));
        }
        repository.save(RegisteredClient.from(existing)
                .clientSecret(passwordEncoder.encode(internalClientSecret))
                .scopes(s -> s.addAll(scopesToAdd))
                .build());
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
