package vn.t3nexus.lib.web.commons.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Mint OAuth2 access token qua client_credentials grant — dùng cho service-to-service call cần
 * xác thực (VD internal endpoint bảo vệ bằng OAuth2 Resource Server, xem
 * SessionRevokeController/WebGatewayRevocationClient — oauth2-service tự mint token gọi web-gateway).
 *
 * Cố ý KHÔNG cache token — caller tần suất thấp (logout, revoke...) thì mint mới mỗi lần đơn giản
 * hơn, không đáng đánh đổi thêm state để tối ưu 1 round-trip. Nếu sau này có caller tần suất cao cần
 * cache, nên thêm ở tầng caller (biết rõ pattern gọi của mình) chứ không nhét sẵn ở đây.
 *
 * Caller tự build {@link RestClient} trỏ đúng base URL của Authorization Server (baseUrl,
 * timeout...) — class này chỉ lo phần request/parse token, không sở hữu connection concern.
 */
@RequiredArgsConstructor
public class ClientCredentialsTokenClient {

    private static final String TOKEN_PATH = "/oauth2/token";

    private final RestClient authorizationServerClient;
    private final String     clientId;
    private final String     clientSecret;

    @SuppressWarnings("unchecked")
    public String fetchToken(String scope) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);

        Map<String, Object> response = authorizationServerClient.post()
                .uri(TOKEN_PATH)
                .headers(h -> h.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        Object accessToken = response != null ? response.get("access_token") : null;
        if (accessToken == null) {
            throw new IllegalStateException("Token response missing access_token");
        }
        return accessToken.toString();
    }
}
