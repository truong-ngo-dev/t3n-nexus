package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import org.springframework.stereotype.Component;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

import java.util.List;

/**
 * Sổ phiên không lưu gì, thay sổ phiên mặc định trong bộ nhớ của Spring Authorization Server ({@code feature 03 tech.md} T2).
 *
 * <p>Sổ mặc định bị Spring dùng để sinh {@code sid} và {@code auth_time} cho ID token (chọn session "đăng ký sau cùng" của người
 * dùng, chọn nhầm khi một người có nhiều thiết bị) và để kiểm {@code sid} của hint lúc đăng xuất. Ta dùng {@code sid} là mã phiên
 * ứng dụng và tự ràng buộc hint với cookie bằng {@code idp_session_id}, nên Spring không được thấy session nào: mọi hàm tra trả rỗng,
 * mọi hàm ghi bỏ qua. Cũng không giữ mã session trong bộ nhớ và không phình dần.
 */
@Component
public class NoSessionRegistry implements SessionRegistry {

    @Override public List<Object> getAllPrincipals() { return List.of(); }

    @Override public List<SessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions) { return List.of(); }

    @Override public SessionInformation getSessionInformation(String sessionId) { return null; }

    @Override public void refreshLastRequest(String sessionId) { }

    @Override public void registerNewSession(String sessionId, Object principal) { }

    @Override public void removeSessionInformation(String sessionId) { }
}
