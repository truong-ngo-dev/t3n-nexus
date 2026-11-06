package vn.t3nexus.oauth2.infrastructure.security.oauth2.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cấu hình cho các ứng dụng BFF giữ mã truy cập (storefront, seller, ops...).
 */
@Component
@ConfigurationProperties(prefix = "app.bffs")
public class BffClientsProperties extends LinkedHashMap<String, BffClientsProperties.Bff> {

    @Getter
    @Setter
    public static class Bff {
        private String clientId;
        private String baseUrl;
        private String secret = "";
        private boolean requireProofKey = true;
        private List<String> redirectUris = List.of();
        private List<String> postLogoutRedirectUris = List.of();
    }

    /**
     * Tìm thông tin cấu hình BFF theo clientId công khai.
     */
    public Optional<Map.Entry<String, Bff>> byClientId(String publicClientId) {
        if (publicClientId == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, Bff> entry : entrySet()) {
            if (entry.getValue() != null && publicClientId.equals(entry.getValue().getClientId())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Trả về scope nội bộ theo tên ứng dụng BFF (vd: storefront.internal).
     */
    public static String internalScope(String appName) {
        return appName + ".internal";
    }
}
