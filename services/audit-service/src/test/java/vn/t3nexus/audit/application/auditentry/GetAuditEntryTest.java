package vn.t3nexus.audit.application.auditentry;

import org.junit.jupiter.api.Test;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntryId;
import vn.t3nexus.audit.domain.auditentry.AuditEntryRepository;
import vn.t3nexus.audit.domain.auditentry.AuditErrorCode;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAuditEntryTest {

    private final AuditEntryRepository repository = mock(AuditEntryRepository.class);
    private final GetAuditEntry useCase = new GetAuditEntry(repository);

    @Test
    void entryExistsReturnsEntry() {
        // Bản ghi tồn tại thì trả về thông tin chi tiết
        UUID id = UUID.randomUUID();
        AuditEntry entry = AuditEntry.restore(
                id,
                "ev-1",
                Instant.now(),
                Instant.now(),
                new AuditActor(UUID.randomUUID(), "ADMIN"),
                "USER_LOGIN",
                new AuditTarget("USER", "u-1"),
                Map.of("ip", "127.0.0.1"),
                "auth-service",
                "trace-1"
        );

        when(repository.findById(AuditEntryId.of(id))).thenReturn(Optional.of(entry));

        GetAuditEntry.Result result = useCase.handle(new GetAuditEntry.Query(id));

        assertThat(result.entry()).isEqualTo(entry);
    }

    @Test
    void entryDoesNotExistThrowsDomainExceptionWith404Status() {
        // Không tìm thấy bản ghi thì ném ngoại lệ AUDIT_ENTRY_NOT_FOUND với HTTP status 404
        UUID notFoundId = UUID.randomUUID();
        when(repository.findById(AuditEntryId.of(notFoundId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.handle(new GetAuditEntry.Query(notFoundId)))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException domainEx = (DomainException) ex;
                    assertThat(domainEx.getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_ENTRY_NOT_FOUND);
                    assertThat(domainEx.getErrorCode().httpStatus()).isEqualTo(404);
                });
    }
}
