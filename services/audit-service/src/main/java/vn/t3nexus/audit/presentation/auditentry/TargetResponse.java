package vn.t3nexus.audit.presentation.auditentry;

/**
 * Thông tin phản hồi về đối tượng bị tác động.
 */
public record TargetResponse(
        String type,
        String id
) {
}
