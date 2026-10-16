package vn.t3nexus.oauth2.domain.login_session;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phần miền của KT-01, 02, 11, 12, 19, 20, 22, 25, 26; INV-ATH-05, 08, 10.
 * Các test này chạy đỏ ở bước 4 (hành vi chưa cài) và xanh sau bước 5.
 */
class LoginSessionTest {

    private static final Instant NOW     = Instant.parse("2026-10-13T03:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofDays(30));
    private static final String  IDP_SESSION_ID = "IDP-SESSION-VALUE-1";

    private final LoginSessionId sessionId = LoginSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000a1"));
    private final UserAccountId  accountId = UserAccountId.of(UUID.fromString("01938a5e-1c00-7000-8000-000000000001"));
    private final DeviceKeyHash  deviceKey = new DeviceKeyHash("hash-of-device-key");
    private final LoginContext   context   = new LoginContext("203.0.113.7", "Mozilla/5.0 Chrome/130");

    private final ClientId shop   = new ClientId("shop-web");
    private final ClientId seller = new ClientId("seller-web");

    private final ClientSessionId sid1 = ClientSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000b1"));
    private final ClientSessionId sid2 = ClientSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000b2"));
    private final ClientSessionId sid3 = ClientSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000b3"));

    private LoginSession opened() {
        LoginSession session = LoginSession.open(sessionId, accountId, deviceKey, IDP_SESSION_ID, context, EXPIRES, NOW);
        session.clearDomainEvents();
        return session;
    }

    // ───────────── open ─────────────

    @Test
    void open_creates_user_session_without_client_sessions() {
        LoginSession session = LoginSession.open(sessionId, accountId, deviceKey, IDP_SESSION_ID, context, EXPIRES, NOW);

        assertThat(session.getId()).isEqualTo(sessionId);
        assertThat(session.userAccountId()).isEqualTo(accountId);
        assertThat(session.deviceKey()).isEqualTo(deviceKey);
        assertThat(session.idpSessionId()).isEqualTo(IDP_SESSION_ID);
        assertThat(session.issuedAt()).isEqualTo(NOW);
        assertThat(session.expiresAt()).isEqualTo(EXPIRES);
        assertThat(session.clientSessions()).isEmpty();
    }

    @Test
    void open_records_one_session_issued_event_with_hashed_device_key_and_context() {
        LoginSession session = LoginSession.open(sessionId, accountId, deviceKey, IDP_SESSION_ID, context, EXPIRES, NOW);

        assertThat(session.getDomainEvents()).hasSize(1);
        SessionIssuedEvent event = (SessionIssuedEvent) session.getDomainEvents().iterator().next();
        assertThat(event.getAggregateId()).isEqualTo(sessionId.getValueAsString());
        assertThat(event.getOccurredOn()).isEqualTo(NOW);
        assertThat(event.getPayload().toString())
                .contains(sessionId.getValueAsString(), accountId.getValueAsString(),
                        "hash-of-device-key", "203.0.113.7", "Mozilla/5.0 Chrome/130");
    }

    @Test
    void open_event_never_carries_the_spring_session_id() {
        // KT-25 (phần event)
        LoginSession session = LoginSession.open(sessionId, accountId, deviceKey, IDP_SESSION_ID, context, EXPIRES, NOW);

        for (DomainEvent event : session.getDomainEvents()) {
            assertThat(event.getPayload().toString()).doesNotContain(IDP_SESSION_ID);
        }
    }

    @Test
    void open_requires_expiry_after_issue_time() {
        assertThatThrownBy(() -> LoginSession.open(sessionId, accountId, deviceKey, IDP_SESSION_ID, context, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────── issueClientSession ─────────────

    @Test
    void issue_creates_a_client_session_when_the_client_has_none() {
        LoginSession session = opened();

        IssuedClientSession issued = session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        assertThat(issued.sid()).isEqualTo(sid1);
        assertThat(session.clientSessions()).singleElement().satisfies(c -> {
            assertThat(c.clientId()).isEqualTo(shop);
            assertThat(c.authorizationId()).isEqualTo("auth-A");
        });
        assertThat(issued.replacedAuthorizationId()).isEqualTo(Optional.empty());
        assertThat(session.clientSessions()).hasSize(1);
    }

    @Test
    void issue_records_client_session_issued_with_event_id_from_the_token_id() {
        LoginSession session = opened();

        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        assertThat(session.getDomainEvents()).hasSize(1);
        ClientSessionIssuedEvent event = (ClientSessionIssuedEvent) session.getDomainEvents().iterator().next();
        assertThat(event.getEventId()).isEqualTo("jti-1");
        assertThat(event.getPayload().toString())
                .contains(sessionId.getValueAsString(), sid1.getValueAsString(), accountId.getValueAsString(),
                        "shop-web", "hash-of-device-key")
                .doesNotContain("203.0.113.7")          // không mang địa chỉ mạng (lời gọi đến từ BFF)
                .doesNotContain(IDP_SESSION_ID)
                .doesNotContain("auth-A");               // authorization_id không ra ngoài
    }

    @Test
    void issue_for_the_same_client_keeps_the_sid_relinks_and_reports_the_replaced_authorization() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        IssuedClientSession again = session.issueClientSession(shop, sid2, "auth-B", "jti-2", NOW.plusSeconds(60));

        assertThat(session.clientSessions()).hasSize(1);                       // INV-ATH-10
        assertThat(again.sid()).isEqualTo(sid1);                                 // sid giữ nguyên, sid2 không dùng
        assertThat(session.clientSessions()).singleElement()
                .satisfies(c -> assertThat(c.authorizationId()).isEqualTo("auth-B"));
        assertThat(again.replacedAuthorizationId()).contains("auth-A");
    }

    @Test
    void issue_for_the_same_client_with_the_same_authorization_replaces_nothing() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        IssuedClientSession again = session.issueClientSession(shop, sid2, "auth-A", "jti-2", NOW.plusSeconds(60));

        assertThat(again.replacedAuthorizationId()).isEmpty();
        assertThat(session.clientSessions()).hasSize(1);
    }

    @Test
    void issue_for_another_client_adds_a_second_client_session() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        session.issueClientSession(seller, sid2, "auth-C", "jti-3", NOW.plusSeconds(5));

        assertThat(session.clientSessions()).hasSize(2);
        assertThat(session.clientSessions()).extracting(ClientSession::clientId).containsExactlyInAnyOrder(shop, seller);
    }

    @Test
    void issue_is_rejected_once_the_session_has_expired() {
        LoginSession session = opened();

        assertThatThrownBy(() -> session.issueClientSession(shop, sid1, "auth-A", "jti-1", EXPIRES))
                .isInstanceOfSatisfying(LoginSessionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.SESSION_EXPIRED));
        assertThat(session.clientSessions()).isEmpty();
    }

    // ───────────── refreshClientSession ─────────────

    @Test
    void refresh_keeps_the_client_session_and_its_authorization_and_records_an_event() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);
        session.clearDomainEvents();

        session.refreshClientSession(sid1, "jti-2", NOW.plusSeconds(900));

        assertThat(session.clientSessions()).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(sid1);
            assertThat(c.authorizationId()).isEqualTo("auth-A");
        });
        assertThat(session.getDomainEvents()).hasSize(1);
        assertThat(session.getDomainEvents().iterator().next().getEventId()).isEqualTo("jti-2");
    }

    @Test
    void refresh_with_a_sid_that_is_not_in_this_session_is_a_mismatch() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        assertThatThrownBy(() -> session.refreshClientSession(sid3, "jti-9", NOW.plusSeconds(10)))
                .isInstanceOfSatisfying(LoginSessionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.CLIENT_SESSION_MISMATCH));
    }

    @Test
    void refresh_is_rejected_once_the_session_has_expired() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        assertThatThrownBy(() -> session.refreshClientSession(sid1, "jti-2", EXPIRES.plusSeconds(1)))
                .isInstanceOfSatisfying(LoginSessionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.SESSION_EXPIRED));
    }

    // ───────────── confirm (INV-ATH-08) ─────────────

    @Test
    void confirm_is_true_before_expiry_and_false_at_and_after_it() {
        LoginSession session = opened();

        assertThat(session.confirm(NOW)).isTrue();
        assertThat(session.confirm(EXPIRES.minusSeconds(1))).isTrue();
        assertThat(session.confirm(EXPIRES)).isFalse();
        assertThat(session.confirm(EXPIRES.plusSeconds(1))).isFalse();
    }

    // ───────────── end (INV-ATH-05) ─────────────

    @Test
    void end_removes_every_client_session_and_records_one_session_ended_with_the_reason() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);
        session.issueClientSession(seller, sid2, "auth-C", "jti-3", NOW);
        session.clearDomainEvents();

        session.end(EndReason.REMOTE_LOGOUT, NOW.plusSeconds(120));

        assertThat(session.clientSessions()).isEmpty();
        assertThat(session.confirm(NOW.plusSeconds(121))).isFalse();
        assertThat(session.getDomainEvents()).hasSize(1);
        SessionEndedEvent event = (SessionEndedEvent) session.getDomainEvents().iterator().next();
        assertThat(event.getPayload().toString()).contains(sessionId.getValueAsString(), "REMOTE_LOGOUT");
    }

    @Test
    void end_twice_succeeds_and_records_only_one_event() {
        LoginSession session = opened();

        session.end(EndReason.SELF_LOGOUT, NOW.plusSeconds(10));
        session.end(EndReason.SELF_LOGOUT, NOW.plusSeconds(20));

        assertThat(session.getDomainEvents().stream().filter(SessionEndedEvent.class::isInstance)).hasSize(1);
    }

    @Test
    void end_succeeds_on_a_session_that_has_already_expired() {
        LoginSession session = opened();

        session.end(EndReason.SELF_LOGOUT, EXPIRES.plusSeconds(1));

        assertThat(session.clientSessions()).isEmpty();
    }

    @Test
    void an_ended_session_cannot_issue_or_refresh_client_sessions() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);
        session.end(EndReason.ACCOUNT_LOCKED, NOW.plusSeconds(5));

        assertThatThrownBy(() -> session.issueClientSession(shop, sid2, "auth-B", "jti-2", NOW.plusSeconds(6)))
                .isInstanceOf(LoginSessionException.class);
        assertThatThrownBy(() -> session.refreshClientSession(sid1, "jti-3", NOW.plusSeconds(6)))
                .isInstanceOf(LoginSessionException.class);
    }

    // ───────────── các việc còn lại ─────────────

    @Test
    void update_idp_session_id_changes_the_value_and_records_no_event() {
        LoginSession session = opened();

        session.updateIdpSessionId("IDP-SESSION-VALUE-2");

        assertThat(session.idpSessionId()).isEqualTo("IDP-SESSION-VALUE-2");
        assertThat(session.getDomainEvents()).isEmpty();
    }

    @Test
    void session_has_no_way_to_extend_its_expiry() {
        assertThat(LoginSession.class.getMethods())
                .noneMatch(m -> m.getName().toLowerCase().contains("extend")
                        || m.getName().equalsIgnoreCase("setExpiresAt")
                        || m.getName().equalsIgnoreCase("renew"));
    }

    @Test
    void client_sessions_cannot_be_modified_from_outside() {
        LoginSession session = opened();
        session.issueClientSession(shop, sid1, "auth-A", "jti-1", NOW);

        List<ClientSession> view = session.clientSessions();

        assertThatThrownBy(view::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reconstitute_keeps_stored_values_and_records_no_event() {
        ClientSession stored = new ClientSession(sid1, shop, "auth-A", NOW);

        LoginSession session = LoginSession.reconstitute(sessionId, accountId, deviceKey, IDP_SESSION_ID, NOW, EXPIRES,
                List.of(stored));

        assertThat(session.getId()).isEqualTo(sessionId);
        assertThat(session.clientSessions()).containsExactly(stored);
        assertThat(session.getDomainEvents()).isEmpty();
    }
}
