package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import java.util.Optional;

/**
 * Nơi lớp bọc {@code OAuth2AuthorizationService} lấy {@code sid} mà BFF gửi kèm yêu cầu làm mới ở {@code /oauth2/token}
 * (ADR-0003 mục 8). Refresh token là chuỗi mờ không mang mã nào, nên {@code sid} đi như một tham số của yêu cầu để tra bản ghi
 * ủy quyền theo khóa chính thay vì quét theo giá trị token.
 */
public interface RequestedSidSource {

    /** {@code sid} của yêu cầu hiện tại; rỗng khi yêu cầu không có hoặc đang ngoài một yêu cầu web. */
    Optional<String> current();
}
