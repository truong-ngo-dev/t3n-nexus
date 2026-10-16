package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.utils.lang.Assert;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Phiên đăng nhập: phiên người dùng (gốc, một lần nhập mật khẩu trên một thiết bị) cùng các phiên ứng dụng bên dưới
 * ({@link ClientSession}). Một aggregate vì INV-ATH-05, 08, 10 cùng ràng buộc phiên người dùng với các phiên con
 * (`service.md` mục 2). Chỉ tồn tại khi đang hoạt động; hết hạn suy ra từ {@code expiresAt}, không lưu trạng thái.
 *
 * <p>Ba hành vi theo tình huống (ADR-0003 mục 5, 9):
 * <ul>
 *   <li>{@link #open}: đăng nhập bằng mật khẩu thành công; chỉ tạo phiên người dùng.</li>
 *   <li>{@link #issueClientSession}: đổi mã lấy token (sau đăng nhập, hoặc xác thực im lặng); khóa tra là {@code clientId}.</li>
 *   <li>{@link #refreshClientSession}: làm mới token; khóa tra là {@code sid}.</li>
 * </ul>
 * Hai hành vi cấp token chỉ được gọi từ điểm móc lúc token đã được cấp (ghi cùng giao dịch với bản ghi ủy quyền).
 *
 * <p>Sau {@link #end} phiên không còn hiệu lực trong bộ nhớ ({@code ended}); use case gọi {@code LoginSessionRepository.delete}
 * sau {@code end}. Cờ này không được lưu: một phiên đọc lại từ kho luôn đang hoạt động.
 */
public class LoginSession extends AbstractAggregateRoot<LoginSessionId> implements AggregateRoot<LoginSessionId> {

    private final UserAccountId       userAccountId;
    private final DeviceKeyHash       deviceKey;
    private       String              idpSessionId;
    private final Instant             issuedAt;
    private final Instant             expiresAt;
    private final List<ClientSession> clientSessions;
    private       boolean             ended;

    private LoginSession(LoginSessionId id, UserAccountId userAccountId, DeviceKeyHash deviceKey,
                         String idpSessionId, Instant issuedAt, Instant expiresAt,
                         List<ClientSession> clientSessions) {
        setId(id);
        this.userAccountId  = userAccountId;
        this.deviceKey      = deviceKey;
        this.idpSessionId   = idpSessionId;
        this.issuedAt       = issuedAt;
        this.expiresAt      = expiresAt;
        this.clientSessions = clientSessions;
        this.ended          = false;
    }

    // ───────────── Factory ─────────────

    /**
     * Mở phiên người dùng: đăng nhập bằng mật khẩu thành công. Chưa có phiên ứng dụng (chưa có bản ghi ủy quyền): phiên
     * ứng dụng đầu tiên do {@link #issueClientSession} cấp lúc đổi mã lấy token. Mã, hạn phiên và thời điểm do use case
     * tính trước rồi truyền vào. Phát {@link SessionIssuedEvent}.
     *
     * @throws IllegalArgumentException khi hạn phiên không sau thời điểm mở
     */
    public static LoginSession open(LoginSessionId id, UserAccountId userAccountId, DeviceKeyHash deviceKey,
                                    String idpSessionId, LoginContext context, Instant expiresAt, Instant now) {
        Assert.notNull(id, "id is required");
        Assert.notNull(userAccountId, "userAccountId is required");
        Assert.notNull(idpSessionId, "idpSessionId is required");
        Assert.notNull(expiresAt, "expiresAt is required");
        Assert.notNull(now, "now is required");
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be after the time the session is opened");
        }
        LoginSession session = new LoginSession(id, userAccountId, deviceKey, idpSessionId, now, expiresAt,
                new ArrayList<>());
        session.addDomainEvent(new SessionIssuedEvent(id, userAccountId, deviceKey, context, now));
        return session;
    }

    public static LoginSession reconstitute(LoginSessionId id, UserAccountId userAccountId, DeviceKeyHash deviceKey,
                                            String idpSessionId, Instant issuedAt, Instant expiresAt,
                                            List<ClientSession> clientSessions) {
        return new LoginSession(id, userAccountId, deviceKey, idpSessionId, issuedAt, expiresAt,
                new ArrayList<>(clientSessions));
    }

    // ───────────── Hành vi ─────────────

    /**
     * Cấp phiên ứng dụng cho một client, lúc đổi mã lấy token (sau đăng nhập mật khẩu, hoặc xác thực im lặng). Khóa tra
     * là {@code clientId}: chưa có phiên cho client đó thì tạo (dùng {@code newClientSessionId}); đã có thì dùng lại, giữ
     * nguyên mã phiên ứng dụng và đổi liên kết sang {@code authorizationId} mới (INV-ATH-10). Kết quả mang mã
     * {@code authorizationId} cũ nếu có đổi để use case xóa bản ghi ủy quyền cũ. Phát {@link ClientSessionIssuedEvent} với mã
     * event lấy từ {@code tokenId} ({@code jti} của access token) để bên nhận bỏ qua trùng.
     *
     * @throws LoginSessionException {@code SESSION_EXPIRED} khi phiên đã quá hạn hoặc đã kết thúc tại {@code now}
     */
    public IssuedClientSession issueClientSession(ClientId clientId, ClientSessionId newClientSessionId,
                                                  String authorizationId, String tokenId, Instant now) {
        Assert.notNull(clientId, "clientId is required");
        Assert.notNull(newClientSessionId, "newClientSessionId is required");
        Assert.notNull(authorizationId, "authorizationId is required");
        Assert.notNull(tokenId, "tokenId is required");
        Assert.notNull(now, "now is required");
        requireActive(now);

        Optional<ClientSession> existing = clientSessions.stream()
                .filter(c -> c.clientId().equals(clientId))
                .findFirst();

        ClientSession clientSession;
        Optional<String> replaced = Optional.empty();
        if (existing.isPresent()) {
            clientSession = existing.get();
            if (!clientSession.authorizationId().equals(authorizationId)) {
                replaced = Optional.of(clientSession.authorizationId());
                clientSession.relink(authorizationId);
            }
        } else {
            clientSession = new ClientSession(newClientSessionId, clientId, authorizationId, now);
            clientSessions.add(clientSession);
        }
        addDomainEvent(new ClientSessionIssuedEvent(tokenId, getId(), clientSession.getId(), userAccountId,
                clientId, deviceKey, now));
        return new IssuedClientSession(clientSession.getId(), replaced);
    }

    /**
     * Làm mới token cho phiên ứng dụng đã biết {@code sid}: kiểm phiên còn hạn và {@code sid} thuộc phiên này; không
     * tạo mới, không đổi liên kết (Spring cập nhật cùng bản ghi tại chỗ). Phát {@link ClientSessionIssuedEvent}.
     *
     * @throws LoginSessionException {@code SESSION_EXPIRED} khi phiên đã quá hạn hoặc đã kết thúc;
     *                               {@code CLIENT_SESSION_MISMATCH} khi {@code sid} không thuộc phiên này
     */
    public void refreshClientSession(ClientSessionId sid, String tokenId, Instant now) {
        Assert.notNull(sid, "sid is required");
        Assert.notNull(tokenId, "tokenId is required");
        Assert.notNull(now, "now is required");
        requireActive(now);

        ClientSession clientSession = clientSessions.stream()
                .filter(c -> c.getId().equals(sid))
                .findFirst()
                .orElseThrow(LoginSessionException::clientSessionMismatch);
        addDomainEvent(new ClientSessionIssuedEvent(tokenId, getId(), clientSession.getId(), userAccountId,
                clientSession.clientId(), deviceKey, now));
    }

    /** Xác nhận: phiên còn hiệu lực tại {@code now} không (suy ra từ hạn, INV-ATH-08). */
    public boolean confirm(Instant now) {
        Assert.notNull(now, "now is required");
        return !ended && now.isBefore(expiresAt);
    }

    /**
     * Kết thúc phiên người dùng và mọi phiên ứng dụng của nó cùng lúc (INV-ATH-05, 08). Phát {@link SessionEndedEvent}
     * kèm lý do. Thành công cả khi đã kết thúc (không phát lại event) hoặc đã hết hạn.
     */
    public void end(EndReason reason, Instant now) {
        Assert.notNull(reason, "reason is required");
        Assert.notNull(now, "now is required");
        if (ended) return;
        ended = true;
        clientSessions.clear();
        addDomainEvent(new SessionEndedEvent(getId(), userAccountId, reason, now));
    }

    /** Cập nhật mã cookie đăng nhập của Spring khi mã session đổi (chống chiếm phiên). Không phát event. */
    public void updateIdpSessionId(String newIdpSessionId) {
        Assert.notNull(newIdpSessionId, "newIdpSessionId is required");
        if (newIdpSessionId.isBlank()) throw new IllegalArgumentException("newIdpSessionId must not be blank");
        this.idpSessionId = newIdpSessionId;
    }

    private void requireActive(Instant now) {
        if (!confirm(now)) throw LoginSessionException.sessionExpired();
    }

    // ───────────── Truy vấn ─────────────

    public UserAccountId userAccountId()  { return userAccountId; }
    public DeviceKeyHash deviceKey()      { return deviceKey; }
    public String        idpSessionId()   { return idpSessionId; }
    public Instant       issuedAt()       { return issuedAt; }
    public Instant       expiresAt()      { return expiresAt; }

    public List<ClientSession> clientSessions() {
        return Collections.unmodifiableList(clientSessions);
    }
}
