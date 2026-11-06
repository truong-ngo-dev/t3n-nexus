package vn.t3nexus.audit.presentation.auditentry;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.audit.application.auditentry.GetAuditEntry;
import vn.t3nexus.audit.application.auditentry.ListAuditEntries;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller cung cấp các điểm cuối tra cứu bản ghi kiểm toán.
 * <br>Bảo đảm chỉ cung cấp phương thức GET và không có thao tác ghi dữ liệu.
 */
@RestController
public class AuditEntryController {

    private final ListAuditEntries listAuditEntries;
    private final GetAuditEntry getAuditEntry;

    public AuditEntryController(ListAuditEntries listAuditEntries, GetAuditEntry getAuditEntry) {
        this.listAuditEntries = listAuditEntries;
        this.getAuditEntry = getAuditEntry;
    }

    @GetMapping("/api/audit/entries")
    public ApiResponse<AuditEntryPageResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        ListAuditEntries.Query query = new ListAuditEntries.Query(
                from,
                to,
                actorId,
                action,
                targetType,
                targetId,
                limit,
                cursor
        );
        ListAuditEntries.Result result = listAuditEntries.handle(query);

        List<AuditEntrySummaryResponse> items = result.items().stream()
                .map(this::toSummaryResponse)
                .toList();

        return ApiResponse.ok(new AuditEntryPageResponse(items, result.nextCursor()));
    }

    @GetMapping("/api/audit/entries/{id}")
    public ApiResponse<AuditEntryDetailResponse> get(@PathVariable UUID id) {
        GetAuditEntry.Result result = getAuditEntry.handle(new GetAuditEntry.Query(id));
        AuditEntry entry = result.entry();

        AuditEntryDetailResponse response = new AuditEntryDetailResponse(
                entry.id(),
                entry.occurredAt(),
                entry.recordedAt(),
                new ActorResponse(entry.actor().accountId(), entry.actor().role()),
                entry.action(),
                new TargetResponse(entry.target().type(), entry.target().id()),
                entry.sourceService(),
                entry.sourceEventId(),
                entry.details(),
                entry.traceId()
        );

        return ApiResponse.ok(response);
    }

    private AuditEntrySummaryResponse toSummaryResponse(AuditEntry entry) {
        return new AuditEntrySummaryResponse(
                entry.id(),
                entry.occurredAt(),
                entry.recordedAt(),
                new ActorResponse(entry.actor().accountId(), entry.actor().role()),
                entry.action(),
                new TargetResponse(entry.target().type(), entry.target().id()),
                entry.sourceService()
        );
    }
}
