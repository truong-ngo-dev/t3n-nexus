package vn.t3nexus.lib.audit;

import vn.t3nexus.lib.events.EventEnvelope;

import java.util.Map;
import java.util.UUID;

/**
 * Đọc và xác thực bản ghi kiểm toán từ khung {@link EventEnvelope}.
 * <br>Bảo đảm không rò rỉ dữ liệu nhạy cảm ra thông báo lỗi và không làm biến đổi khung gốc.
 */
public final class AuditRecordReader {

    private AuditRecordReader() {
    }

    public static AuditRecord read(EventEnvelope envelope) {
        if (envelope == null) {
            throw new InvalidAuditRecordException("envelope");
        }

        if (!AuditContract.isEventId(envelope.eventId())) {
            throw new InvalidAuditRecordException("eventId");
        }

        if (!AuditContract.EVENT_TYPE.equals(envelope.eventType())) {
            throw new InvalidAuditRecordException("eventType");
        }

        if (envelope.occurredAt() == null) {
            throw new InvalidAuditRecordException("occurredAt");
        }

        if (envelope.schemaVersion() == null || envelope.schemaVersion() < 1) {
            throw new InvalidAuditRecordException("schemaVersion");
        }

        if (!(envelope.payload() instanceof Map<?, ?> payloadMap)) {
            throw new InvalidAuditRecordException("payload");
        }

        Object sourceServiceObj = payloadMap.get("sourceService");
        if (!(sourceServiceObj instanceof String sourceService) || !AuditContract.isSourceService(sourceService)) {
            throw new InvalidAuditRecordException("sourceService");
        }

        Object actorObj = payloadMap.get("actor");
        if (!(actorObj instanceof Map<?, ?> actorMap)) {
            throw new InvalidAuditRecordException("actor");
        }

        Object roleObj = actorMap.get("role");
        if (!(roleObj instanceof String role) || !AuditContract.isRole(role)) {
            throw new InvalidAuditRecordException("actor.role");
        }

        Object accountIdObj = actorMap.get("accountId");
        UUID accountId = null;
        if (AuditContract.SYSTEM_ROLE.equals(role)) {
            if (accountIdObj != null) {
                if (!(accountIdObj instanceof String sAcc)) {
                    throw new InvalidAuditRecordException("actor.accountId");
                }
                try {
                    accountId = UUID.fromString(sAcc);
                } catch (IllegalArgumentException e) {
                    throw new InvalidAuditRecordException("actor.accountId");
                }
            }
        } else {
            if (!(accountIdObj instanceof String sAcc)) {
                throw new InvalidAuditRecordException("actor.accountId");
            }
            try {
                accountId = UUID.fromString(sAcc);
            } catch (IllegalArgumentException e) {
                throw new InvalidAuditRecordException("actor.accountId");
            }
        }

        AuditActor actor = new AuditActor(accountId, role);

        Object actionObj = payloadMap.get("action");
        if (!(actionObj instanceof String action) || !AuditContract.isAction(action)) {
            throw new InvalidAuditRecordException("action");
        }

        Object targetObj = payloadMap.get("target");
        if (!(targetObj instanceof Map<?, ?> targetMap)) {
            throw new InvalidAuditRecordException("target");
        }

        Object targetTypeObj = targetMap.get("type");
        if (!(targetTypeObj instanceof String targetType) || !AuditContract.isTargetType(targetType)) {
            throw new InvalidAuditRecordException("target.type");
        }

        Object targetIdObj = targetMap.get("id");
        if (!(targetIdObj instanceof String targetId) || !AuditContract.isTargetId(targetId)) {
            throw new InvalidAuditRecordException("target.id");
        }

        AuditTarget target = new AuditTarget(targetType, targetId);

        Object detailsObj = payloadMap.get("details");
        Map<?, ?> rawDetails;
        if (detailsObj == null) {
            rawDetails = Map.of();
        } else if (detailsObj instanceof Map<?, ?> map) {
            rawDetails = map;
        } else {
            throw new InvalidAuditRecordException("details");
        }

        AuditDetailsPolicy.Result detailsResult = AuditDetailsPolicy.lenient(rawDetails);

        String traceId = envelope.traceId();
        if (traceId != null && traceId.isBlank()) {
            traceId = null;
        }

        return new AuditRecord(
                envelope.eventId(),
                envelope.occurredAt(),
                traceId,
                sourceService,
                actor,
                action,
                target,
                detailsResult.kept(),
                detailsResult.dropped()
        );
    }
}
