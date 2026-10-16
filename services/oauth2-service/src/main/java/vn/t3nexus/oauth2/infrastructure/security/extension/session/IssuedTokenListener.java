package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

/**
 * Nhận biết một lần cấp token cho phiên người dùng đã hoàn tất (bản ghi ủy quyền đã lưu và phiên ứng dụng đã ghi nhận, giao dịch đã
 * xong). Chỉ để quan sát, không được đổi kết quả cấp token: lỗi của bên nhận bị bỏ qua. Hiện chỉ có bên nhận ở profile {@code dev}
 * ({@code DevIssuedTokenLogger}); môi trường khác không có bên nhận nào nên không tốn gì.
 */
@FunctionalInterface
public interface IssuedTokenListener {

    void onTokenIssued(OAuth2Authorization authorization);
}
