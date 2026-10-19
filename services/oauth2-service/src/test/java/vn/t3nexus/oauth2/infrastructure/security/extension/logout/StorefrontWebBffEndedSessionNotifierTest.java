package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession.EndedClientSession;
import vn.t3nexus.oauth2.infrastructure.adapter.http.StorefrontWebBffRevocationClient;

import java.util.List;

/**
 * Báo ứng dụng khi phiên kết thúc: một lời gọi cho mỗi phiên ứng dụng của storefront-web-bff, theo `sid` (KT-07). Cột `client_id` của phiên
 * ứng dụng giữ mã nội bộ của client đã đăng ký, không phải `client_id` công khai (lỗi bước 8: so thẳng chữ "storefront-web" không khớp).
 */
class StorefrontWebBffEndedSessionNotifierTest {

    private static final String STOREFRONT_WEB_ID = "storefront-web-client-id";   // mã nội bộ, như ở cơ sở dữ liệu thật
    private static final String POSTMAN_ID     = "9d1f0c5e-postman-internal-id";

    private final StorefrontWebBffRevocationClient client = Mockito.mock(StorefrontWebBffRevocationClient.class);
    private final RegisteredClientRepository clients = Mockito.mock(RegisteredClientRepository.class);
    private final StorefrontWebBffEndedSessionNotifier notifier = new StorefrontWebBffEndedSessionNotifier(client, clients);

    private static RegisteredClient registered(String internalId, String publicClientId) {
        return RegisteredClient.withId(internalId).clientId(publicClientId).clientSecret("{noop}x")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/cb").build();
    }

    private void knownClients() {
        Mockito.when(clients.findById(STOREFRONT_WEB_ID)).thenReturn(registered(STOREFRONT_WEB_ID, "storefront-web"));
        Mockito.when(clients.findById(POSTMAN_ID)).thenReturn(registered(POSTMAN_ID, "postman-dev"));
    }

    @Test
    void it_tells_the_web_gateway_once_per_client_session_by_sid() {
        knownClients();

        notifier.notifyEnded(List.of(
                new EndedClientSession("sid-1", STOREFRONT_WEB_ID, "auth-1"),
                new EndedClientSession("sid-2", STOREFRONT_WEB_ID, "auth-2")));

        Mockito.verify(client).revoke("sid-1");
        Mockito.verify(client).revoke("sid-2");
        Mockito.verifyNoMoreInteractions(client);
    }

    @Test
    void sessions_of_other_clients_are_not_reported_because_they_have_no_bff_to_tell() {
        knownClients();

        notifier.notifyEnded(List.of(new EndedClientSession("sid-3", POSTMAN_ID, "auth-3")));

        Mockito.verifyNoInteractions(client);
    }

    @Test
    void a_client_that_no_longer_exists_is_skipped_without_failing_the_rest() {
        knownClients();

        notifier.notifyEnded(List.of(
                new EndedClientSession("sid-4", "deleted-client-id", "auth-4"),
                new EndedClientSession("sid-5", STOREFRONT_WEB_ID, "auth-5")));

        Mockito.verify(client).revoke("sid-5");
        Mockito.verifyNoMoreInteractions(client);
    }

    @Test
    void an_empty_list_calls_nothing() {
        notifier.notifyEnded(List.of());

        Mockito.verifyNoInteractions(client);
    }
}
