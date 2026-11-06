package vn.t3nexus.audit.presentation.auditentry;

import java.util.UUID;

/**
 * Thông tin phản hồi về chủ thể thực hiện hành động.
 */
public record ActorResponse(
        UUID accountId,
        String role
) {
}
