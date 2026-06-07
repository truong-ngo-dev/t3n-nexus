package vn.t3nexus.oauth2.infrastructure.adapter.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class WebGatewayRevocationClient {

    private final RestClient restClient;

    public WebGatewayRevocationClient(@Value("${app.webgateway.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void revoke(String ossId) {
        try {
            restClient.post()
                    .uri("/webgw/internal/sessions/revoke")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ossId", ossId))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("[WebGatewayRevocation] revoked ossId={}", ossId);
        } catch (Exception e) {
            log.warn("[WebGatewayRevocation] failed for ossId={}: {}", ossId, e.getMessage());
        }
    }
}
