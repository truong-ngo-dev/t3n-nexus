package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession;

import java.util.List;

/**
 * Báo các ứng dụng giữ mã (BFF) xóa mã của chúng sau khi phiên đã kết thúc, theo mã phiên ứng dụng ({@code sid}). Gọi <b>sau khi</b>
 * giao dịch đăng xuất đã commit. Cố gắng hết sức: không với tới được thì ghi cảnh báo, không làm hỏng đăng xuất (mã làm mới đã bị
 * xóa, mã truy cập hết hạn ngắn là lưới an toàn). Chưa có hàng đợi thử lại ở C0.
 */
public interface EndedSessionNotifier {

    void notifyEnded(List<EndLoginSession.EndedClientSession> ended);
}
