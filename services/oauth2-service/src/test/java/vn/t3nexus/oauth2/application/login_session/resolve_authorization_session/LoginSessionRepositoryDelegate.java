package vn.t3nexus.oauth2.application.login_session.resolve_authorization_session;

import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bọc một kho phiên để test ghi đè từng phương thức (đếm lần ghi, giả lập lỗi cơ sở dữ liệu). */
public class LoginSessionRepositoryDelegate implements LoginSessionRepository {

    private final LoginSessionRepository delegate;

    public LoginSessionRepositoryDelegate(LoginSessionRepository delegate) {
        this.delegate = delegate;
    }

    @Override public Optional<LoginSession> findById(LoginSessionId id) { return delegate.findById(id); }
    @Override public void save(LoginSession aggregate) { delegate.save(aggregate); }
    @Override public void delete(LoginSessionId id) { delegate.delete(id); }
    @Override public Optional<LoginSession> findByClientSessionId(ClientSessionId sid) { return delegate.findByClientSessionId(sid); }
    @Override public Optional<String> findAuthorizationIdByClientSessionId(ClientSessionId sid) {
        return delegate.findAuthorizationIdByClientSessionId(sid);
    }
    @Override public List<LoginSession> findActiveByAccount(UserAccountId accountId, Instant now) {
        return delegate.findActiveByAccount(accountId, now);
    }
    @Override public List<LoginSession> findActiveByAccountAndDevice(UserAccountId accountId, DeviceKeyHash deviceKey, Instant now) {
        return delegate.findActiveByAccountAndDevice(accountId, deviceKey, now);
    }
    @Override public List<LoginSession> findExpired(Instant now, int limit) { return delegate.findExpired(now, limit); }
}
