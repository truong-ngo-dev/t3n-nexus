package vn.t3nexus.oauth2.domain.login_session;

import java.util.Optional;

/**
 * Kết quả của {@link LoginSession#issueClientSession} cho use case ở điểm móc lúc token được cấp: mã phiên ứng dụng thực
 * tế ({@code sid}, để đối chiếu với {@code sid} đã nằm trong token) và, khi có đổi liên kết, mã {@code authorizationId} cũ
 * để xóa bản ghi ủy quyền cũ trong cùng giao dịch. Không phải khái niệm miền để dùng lại.
 */
public record IssuedClientSession(ClientSessionId sid, Optional<String> replacedAuthorizationId) {
}
