package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Đồ giả của {@link OAuth2AuthorizationService} với ngữ nghĩa của kho thật: <b>một hàng cho mỗi mã</b>, lưu đè theo mã, xóa
 * theo mã. {@code InMemoryOAuth2AuthorizationService} của Spring giữ hai bản đồ (mới khởi tạo, hoàn tất) nên sau khi xóa bản
 * hoàn tất, {@code findById} vẫn có thể trả bản khởi tạo cũ, khác với {@code JdbcOAuth2AuthorizationService}.
 */
class SingleRowAuthorizationService implements OAuth2AuthorizationService {

    private final Map<String, OAuth2Authorization> rows = new LinkedHashMap<>();

    @Override public void save(OAuth2Authorization authorization) { rows.put(authorization.getId(), authorization); }

    @Override public void remove(OAuth2Authorization authorization) { rows.remove(authorization.getId()); }

    @Override public OAuth2Authorization findById(String id) { return rows.get(id); }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return rows.values().stream().filter(a -> hasToken(a, token)).findFirst().orElse(null);
    }

    private static boolean hasToken(OAuth2Authorization authorization, String value) {
        return valueOf(authorization.getAccessToken()).equals(value)
                || valueOf(authorization.getRefreshToken()).equals(value)
                || valueOf(authorization.getToken(OAuth2AuthorizationCode.class)).equals(value);
    }

    private static String valueOf(OAuth2Authorization.Token<? extends OAuth2Token> token) {
        return token == null ? "" : token.getToken().getTokenValue();
    }
}
