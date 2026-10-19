package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession;
import vn.t3nexus.oauth2.infrastructure.adapter.http.StorefrontWebBffRevocationClient;

import java.util.List;

/**
 * Báo storefront-web-bff (BFF của người dùng) qua lời gọi nội bộ có sẵn, một lời gọi cho mỗi phiên ứng dụng vừa kết thúc.
 * Chỉ báo cho phiên của client {@code storefront-web}; client khác chưa có BFF để báo ở C0.
 *
 * <p>{@code client_sessions.client_id} giữ mã <b>nội bộ</b> của client đã đăng ký ({@code RegisteredClient.getId()}, vì điểm móc lúc cấp
 * token chỉ có {@code OAuth2Authorization.getRegisteredClientId()}), không phải {@code client_id} công khai như {@code storefront-web}.
 * Nên ở đây tra lại client để biết phiên thuộc ứng dụng nào. Lần chạy thật bước 8 của feature 03 từng cho thấy so thẳng chữ
 * {@code storefront-web} với cột đó không bao giờ khớp nên BFF không được báo. Nội dung lời gọi: {@code feature 03 tech.md} T7.
 */
@Component
@RequiredArgsConstructor
public class StorefrontWebBffEndedSessionNotifier implements EndedSessionNotifier {

    private static final String STOREFRONT_WEB = "storefront-web";

    private final StorefrontWebBffRevocationClient  revocationClient;
    private final RegisteredClientRepository  clients;

    @Override
    public void notifyEnded(List<EndLoginSession.EndedClientSession> ended) {
        for (EndLoginSession.EndedClientSession session : ended) {
            RegisteredClient client = clients.findById(session.clientId());
            if (client != null && STOREFRONT_WEB.equals(client.getClientId())) revocationClient.revoke(session.sid());
        }
    }
}
