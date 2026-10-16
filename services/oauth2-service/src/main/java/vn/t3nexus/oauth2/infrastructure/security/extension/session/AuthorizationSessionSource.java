package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;

import java.util.Optional;

/**
 * Nơi lớp bọc {@code OAuth2AuthorizationService} lấy kết quả của cửa kiểm hạn ({@link ResolveAuthorizationSession}) cho yêu cầu
 * cấp mã đang xử lý. Cửa kiểm hạn chạy trước, ở đầu yêu cầu {@code /oauth2/authorize} (một bộ lọc, bước 9a.7); lớp bọc chỉ đọc
 * kết quả ở thời điểm Spring lưu bản ghi ủy quyền có mã. Tách thành cổng để lớp bọc không phụ thuộc phiên HTTP và test được.
 */
public interface AuthorizationSessionSource {

    /** Kết quả của cửa kiểm hạn cho yêu cầu hiện tại; rỗng khi yêu cầu chưa qua cửa (không có phiên đăng nhập hợp lệ). */
    Optional<ResolveAuthorizationSession.Result> current();
}
