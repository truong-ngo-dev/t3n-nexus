package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * Đọc claim chuỗi từ token truy cập mà BFF vừa nhận qua kênh máy chủ tới Xác thực. Không kiểm chữ ký: token chưa từng qua trình duyệt,
 * và BFF chỉ cần {@code sid}, {@code role} (hợp đồng danh tính, ADR-0003). Thiếu claim hay token hỏng thì trả {@code null}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessTokenClaims {

    public static final String SID  = "sid";
    public static final String ROLE = "role";

    private final ObjectMapper objectMapper;

    public String text(String jwt, String claim) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(pad(parts[1])));
            JsonNode value = payload.get(claim);
            return value != null && !value.isNull() ? value.asString() : null;
        } catch (Exception e) {
            log.warn("[AccessTokenClaims] Failed to read claim {}: {}", claim, e.getMessage());
            return null;
        }
    }

    private static String pad(String base64Url) {
        int mod = base64Url.length() % 4;
        return mod == 0 ? base64Url : base64Url + "=".repeat(4 - mod);
    }
}
