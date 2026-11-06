package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession;
import vn.t3nexus.oauth2.infrastructure.adapter.http.BffRevocationClient;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.client.BffClientsProperties;

import java.util.List;

/**
 * Báo các BFF qua lời gọi nội bộ có sẵn, một lời gọi cho mỗi phiên ứng dụng vừa kết thúc.
 * Chỉ báo cho phiên của các client thuộc danh sách BFF được cấu hình.
 *
 * <p>{@code client_sessions.client_id} giữ mã <b>nội bộ</b> của client đã đăng ký ({@code RegisteredClient.getId()}, vì điểm móc lúc cấp
 * token chỉ có {@code OAuth2Authorization.getRegisteredClientId()}), không phải {@code client_id} công khai như {@code storefront-web}.
 * Nên ở đây tra lại client để biết phiên thuộc ứng dụng nào. Lần chạy thật bước 8 của feature 03 từng cho thấy so thẳng chữ
 * {@code storefront-web} với cột đó không bao giờ khớp nên BFF không được báo. Nội dung lời gọi: {@code feature 03 tech.md} T7.
 */
@Component
@RequiredArgsConstructor
public class BffEndedSessionNotifier implements EndedSessionNotifier {

    private final BffRevocationClient revocationClient;
    private final RegisteredClientRepository clients;
    private final BffClientsProperties bffs;

    @Override
    public void notifyEnded(List<EndLoginSession.EndedClientSession> ended) {
        for (EndLoginSession.EndedClientSession session : ended) {
            RegisteredClient client = clients.findById(session.clientId());
            if (client != null && bffs.byClientId(client.getClientId()).isPresent()) {
                revocationClient.revoke(client.getClientId(), session.sid());
            }
        }
    }
}
