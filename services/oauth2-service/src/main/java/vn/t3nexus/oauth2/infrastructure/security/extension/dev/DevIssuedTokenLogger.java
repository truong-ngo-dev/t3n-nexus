package vn.t3nexus.oauth2.infrastructure.security.extension.dev;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.IssuedTokenListener;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.SessionBinding;

/**
 * Ghi token ra log mỗi khi luồng đăng nhập thật cấp token, để lấy token thật (gắn phiên thật, đúng tài khoản, vai và client) dán vào
 * Postman hoặc `.http` khi test tay các dịch vụ khác. Chỉ tồn tại khi <b>cả hai</b> đúng: profile {@code dev} và cờ
 * {@code observability.logging.reveal-secrets=true} (cờ này tự từ chối khởi động nếu bật ở profile khác, `logging.md`). Thiếu một trong
 * hai thì bean không có, không dòng log nào được ghi; bộ che bí mật của log JSON chỉ nhả token khi cờ bật.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "observability.logging.reveal-secrets", havingValue = "true")
public class DevIssuedTokenLogger implements IssuedTokenListener {

    private final RegisteredClientRepository clients;

    public DevIssuedTokenLogger(RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public void onTokenIssued(OAuth2Authorization authorization) {
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        String clientId = client == null ? authorization.getRegisteredClientId() : client.getClientId();
        String sid = SessionBinding.from(authorization).map(SessionBinding::sid).orElse("-");
        log.info("dev token issued: sub={} client={} sid={} exp={} access_token={} refresh_token={} id_token={}",
                authorization.getPrincipalName(), clientId, sid, expiresAt(authorization),
                value(authorization.getToken(OAuth2AccessToken.class)), value(authorization.getToken(OAuth2RefreshToken.class)),
                value(authorization.getToken(OidcIdToken.class)));
    }

    private static String expiresAt(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization.getToken(OAuth2AccessToken.class);
        return access == null || access.getToken().getExpiresAt() == null ? "-" : access.getToken().getExpiresAt().toString();
    }

    private static String value(OAuth2Authorization.Token<?> token) {
        return token == null ? "-" : token.getToken().getTokenValue();
    }
}
