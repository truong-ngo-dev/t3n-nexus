package vn.t3nexus.oauth2.infrastructure.adapter.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.t3nexus.lib.web.commons.client.ClientCredentialsTokenClient;

import java.util.Map;

/**
 * <p>Back-channel session revoke sang <b>web-gateway</b> (<code>POST /webgw/internal/sessions/revoke</code>) &mdash;
 * server-to-server, <b>KHÔNG</b> qua api-gateway (cùng lý do các call nội bộ khác trong service này, xem
 * <code>app.oauth2.internal-base-url</code>).</p>
 *
 * <p>Xác thực bằng service-account JWT: mint token <b>MỚI</b> mỗi lần gọi qua OAuth2 <code>client_credentials</code> grant
 * (client <code>"oauth2-service-internal"</code>, scope <code>"webgw.internal"</code>, seed ở migration <code>V10</code>) &mdash;
 * xem {@link ClientCredentialsTokenClient} (<code>common-web</code>) cho phần mint token dùng chung. web-gateway
 * verify token qua JWKS đã có sẵn (<code>spring.security.oauth2.resourceserver.jwt.jwk-set-uri</code>,
 * web-gateway <code>SecurityConfiguration</code>).</p>
 */
@Slf4j
@Component
public class WebGatewayRevocationClient {

    private static final String INTERNAL_SCOPE = "webgw.internal";

    private final RestClient                   revokeClient;
    private final ClientCredentialsTokenClient  tokenClient;

    public WebGatewayRevocationClient(
            @Value("${app.webgateway.base-url}") String webGatewayBaseUrl,
            @Value("${app.oauth2.internal-base-url}") String selfBaseUrl,
            @Value("${app.internal-client.client-id}") String clientId,
            @Value("${app.internal-client.client-secret}") String clientSecret) {
        // Internal call cùng mạng — bình thường trả lời trong vài ms, nhưng vẫn phải bounded để
        // không giữ thread logout vô thời hạn nếu phía kia treo. Nới rộng hơn mức tối thiểu vì máy
        // dev có thể chậm hơn môi trường thật — vẫn hữu hạn, không phải default không giới hạn.
        ClientHttpRequestFactory requestFactory = timeoutRequestFactory();

        RestClient tokenEndpointClient = RestClient.builder().baseUrl(selfBaseUrl).requestFactory(requestFactory).build();
        this.tokenClient  = new ClientCredentialsTokenClient(tokenEndpointClient, clientId, clientSecret);
        this.revokeClient = RestClient.builder().baseUrl(webGatewayBaseUrl).requestFactory(requestFactory).build();
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(8_000);
        return factory;
    }

    public void revoke(String ossId) {
        // Tách riêng try/catch cho mint-token vs gọi-revoke — 2 lỗi này nguyên nhân khác hẳn nhau
        // (sai client credential vs web-gateway down/token bị reject) nhưng trước đây gộp chung 1
        // log, không phân biệt được lỗi nằm ở bước nào khi debug.
        String token;
        try {
            token = tokenClient.fetchToken(INTERNAL_SCOPE);
        } catch (Exception e) {
            log.warn("[WebGatewayRevocation] failed to mint internal token for ossId={}: {}", ossId, e.getMessage());
            return;
        }

        try {
            revokeClient.post()
                    .uri("/webgw/internal/sessions/revoke")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ossId", ossId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[WebGatewayRevocation] revoked ossId={}", ossId);
        } catch (Exception e) {
            // Best-effort — logout đã hoàn tất phía user (IDP session + OAuthSession đã xoá ở bước
            // trước rồi), thất bại ở đây (timeout, web-gateway down, token bị reject...) không abort
            // flow. Redis mapping [A1]/[A2] orphan tối đa tới TTL 24h — xem design.md Failure Scenarios.
            log.info("[WebGatewayRevocation] revoke call failed for ossId={}: {}", ossId, e.getMessage());
        }
    }
}
