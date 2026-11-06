package vn.t3nexus.oauth2.infrastructure.adapter.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.t3nexus.lib.web.commons.client.ClientCredentialsTokenClient;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.client.BffClientsProperties;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Back-channel báo BFF xóa mã của một phiên ứng dụng vừa kết thúc (<code>POST /bff/internal/sessions/revoke</code>,
 * thân <code>{"sid": "&lt;mã phiên ứng dụng&gt;"}</code>, hợp đồng ở feature 03 <code>tech.md</code> T7) &mdash;
 * server-to-server, <b>KHÔNG</b> qua api-gateway (cùng lý do các call nội bộ khác trong service này, xem
 * <code>app.oauth2.internal-base-url</code>).</p>
 *
 * <p>Xác thực bằng service-account JWT: mint token <b>MỚI</b> mỗi lần gọi qua OAuth2 <code>client_credentials</code> grant
 * (client <code>"oauth2-service-internal"</code>, scope <code>"&lt;appName&gt;.internal"</code>, seed ở migration <code>V10</code>) &mdash;
 * xem {@link ClientCredentialsTokenClient} (<code>common-web</code>) cho phần mint token dùng chung. BFF
 * verify token qua JWKS đã có sẵn (<code>spring.security.oauth2.resourceserver.jwt.jwk-set-uri</code>).</p>
 */
@Slf4j
@Component
public class BffRevocationClient {

    private final BffClientsProperties bffs;
    private final ClientCredentialsTokenClient tokenClient;
    private final ClientHttpRequestFactory requestFactory;
    private final Map<String, RestClient> revokeClients = new ConcurrentHashMap<>();

    public BffRevocationClient(
            BffClientsProperties bffs,
            @Value("${app.oauth2.internal-base-url}") String selfBaseUrl,
            @Value("${app.internal-client.client-id}") String clientId,
            @Value("${app.internal-client.client-secret}") String clientSecret) {
        this.bffs = bffs;
        this.requestFactory = timeoutRequestFactory();
        RestClient tokenEndpointClient = RestClient.builder().baseUrl(selfBaseUrl).requestFactory(requestFactory).build();
        this.tokenClient = new ClientCredentialsTokenClient(tokenEndpointClient, clientId, clientSecret);
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(8_000);
        return factory;
    }

    public void revoke(String bffClientId, String sid) {
        Optional<Map.Entry<String, BffClientsProperties.Bff>> entryOpt = bffs.byClientId(bffClientId);
        if (entryOpt.isEmpty()) {
            log.warn("[BffRevocation] unknown bffClientId={}", bffClientId);
            return;
        }

        String appName = entryOpt.get().getKey();
        BffClientsProperties.Bff bff = entryOpt.get().getValue();
        String scope = BffClientsProperties.internalScope(appName);

        String token;
        try {
            token = tokenClient.fetchToken(scope);
        } catch (Exception e) {
            log.warn("[BffRevocation] failed to mint internal token for sid={}: {}", sid, e.getMessage());
            return;
        }

        try {
            RestClient client = revokeClients.computeIfAbsent(appName, k ->
                    RestClient.builder().baseUrl(bff.getBaseUrl()).requestFactory(requestFactory).build()
            );
            client.post()
                    .uri("/bff/internal/sessions/revoke")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("sid", sid))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[BffRevocation] revoked sid={}", sid);
        } catch (Exception e) {
            log.info("[BffRevocation] revoke call failed for sid={}: {}", sid, e.getMessage());
        }
    }
}
