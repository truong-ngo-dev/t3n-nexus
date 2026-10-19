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
 * <p>Back-channel báo storefront-web-bff xóa mã của một phiên ứng dụng vừa kết thúc (<code>POST /bff/internal/sessions/revoke</code>,
 * thân <code>{"sid": "&lt;mã phiên ứng dụng&gt;"}</code>, hợp đồng ở feature 03 <code>tech.md</code> T7) &mdash;
 * server-to-server, <b>KHÔNG</b> qua api-gateway (cùng lý do các call nội bộ khác trong service này, xem
 * <code>app.oauth2.internal-base-url</code>).</p>
 *
 * <p>Xác thực bằng service-account JWT: mint token <b>MỚI</b> mỗi lần gọi qua OAuth2 <code>client_credentials</code> grant
 * (client <code>"oauth2-service-internal"</code>, scope <code>"storefront.internal"</code>, seed ở migration <code>V10</code>) &mdash;
 * xem {@link ClientCredentialsTokenClient} (<code>common-web</code>) cho phần mint token dùng chung. storefront-web-bff
 * verify token qua JWKS đã có sẵn (<code>spring.security.oauth2.resourceserver.jwt.jwk-set-uri</code>,
 * storefront-web-bff <code>SecurityConfiguration</code>).</p>
 */
@Slf4j
@Component
public class StorefrontWebBffRevocationClient {

    private static final String INTERNAL_SCOPE = "storefront.internal";

    private final RestClient                   revokeClient;
    private final ClientCredentialsTokenClient  tokenClient;

    public StorefrontWebBffRevocationClient(
            @Value("${app.storefront-web-bff.base-url}") String storefrontWebBffBaseUrl,
            @Value("${app.oauth2.internal-base-url}") String selfBaseUrl,
            @Value("${app.internal-client.client-id}") String clientId,
            @Value("${app.internal-client.client-secret}") String clientSecret) {
        // Internal call cùng mạng — bình thường trả lời trong vài ms, nhưng vẫn phải bounded để
        // không giữ thread logout vô thời hạn nếu phía kia treo. Nới rộng hơn mức tối thiểu vì máy
        // dev có thể chậm hơn môi trường thật — vẫn hữu hạn, không phải default không giới hạn.
        ClientHttpRequestFactory requestFactory = timeoutRequestFactory();

        RestClient tokenEndpointClient = RestClient.builder().baseUrl(selfBaseUrl).requestFactory(requestFactory).build();
        this.tokenClient  = new ClientCredentialsTokenClient(tokenEndpointClient, clientId, clientSecret);
        this.revokeClient = RestClient.builder().baseUrl(storefrontWebBffBaseUrl).requestFactory(requestFactory).build();
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(8_000);
        return factory;
    }

    public void revoke(String sid) {
        // Tách riêng try/catch cho mint-token vs gọi-revoke — 2 lỗi này nguyên nhân khác hẳn nhau
        // (sai client credential vs storefront-web-bff down/token bị reject) nhưng trước đây gộp chung 1
        // log, không phân biệt được lỗi nằm ở bước nào khi debug.
        String token;
        try {
            token = tokenClient.fetchToken(INTERNAL_SCOPE);
        } catch (Exception e) {
            log.warn("[StorefrontWebBffRevocation] failed to mint internal token for sid={}: {}", sid, e.getMessage());
            return;
        }

        try {
            revokeClient.post()
                    .uri("/bff/internal/sessions/revoke")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("sid", sid))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[StorefrontWebBffRevocation] revoked sid={}", sid);
        } catch (Exception e) {
            // Best-effort — logout đã hoàn tất phía người dùng (phiên và bản ghi ủy quyền đã xóa trong giao dịch đăng xuất, trước
            // bước này), thất bại ở đây (timeout, storefront-web-bff down, token bị reject...) không abort
            // flow. Redis mapping [A1]/[A2] orphan tối đa tới TTL 24h — xem design.md Failure Scenarios.
            log.info("[StorefrontWebBffRevocation] revoke call failed for sid={}: {}", sid, e.getMessage());
        }
    }
}
