package vn.t3nexus.audit.application.auditentry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.audit.application.auditentry.AuditCursor;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.application.auditentry.AuditEntryFilter;
import vn.t3nexus.audit.domain.auditentry.AuditErrorCode;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListAuditEntriesTest {

    private final AuditEntryQuery repository = mock(AuditEntryQuery.class);
    private final ListAuditEntries useCase = new ListAuditEntries(repository);

    private AuditEntry createEntry(Instant occurredAt, UUID id) {
        return AuditEntry.restore(
                id,
                "ev-" + UUID.randomUUID(),
                occurredAt,
                occurredAt,
                new AuditActor(UUID.randomUUID(), "ADMIN"),
                "ACTION_X",
                new AuditTarget("TARGET", "tgt-1"),
                Map.of(),
                "service-x",
                "trace-x"
        );
    }

    @Test
    void defaultLimitIs50AndCallsSearchWithLimit51() {
        // Cỡ trang mặc định là 50 thì gọi repository với limit + 1 = 51 để thăm dò xem còn trang kế tiếp không
        when(repository.search(any(), isNull(), eq(51))).thenReturn(List.of());

        ListAuditEntries.Query query = new ListAuditEntries.Query(null, null, null, null, null, null, null, null);
        ListAuditEntries.Result result = useCase.handle(query);

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        verify(repository).search(any(), isNull(), eq(51));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 201})
    void invalidLimitThrowsDomainExceptionAndRepositoryIsNotCalled(int invalidLimit) {
        // Limit nằm ngoài [1, 200] bị từ chối ngay mà không truy vấn cơ sở dữ liệu
        ListAuditEntries.Query query = new ListAuditEntries.Query(null, null, null, null, null, null, invalidLimit, null);

        assertThatThrownBy(() -> useCase.handle(query))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_LIMIT_INVALID));

        verifyNoInteractions(repository);
    }

    @Test
    void fromAfterToThrowsTimeRangeInvalidException() {
        // from lớn hơn to là khoảng thời gian không hợp lệ
        Instant from = Instant.parse("2026-10-25T12:00:00Z");
        Instant to = Instant.parse("2026-10-25T11:00:00Z");

        ListAuditEntries.Query query = new ListAuditEntries.Query(from, to, null, null, null, null, null, null);

        assertThatThrownBy(() -> useCase.handle(query))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_TIME_RANGE_INVALID));

        verifyNoInteractions(repository);
    }

    @Test
    void fromEqualsToIsValid() {
        // from bằng to là hợp lệ (tìm kiếm khoảng rỗng hoặc thời điểm chính xác)
        Instant sameTime = Instant.parse("2026-10-25T12:00:00Z");
        when(repository.search(any(), isNull(), anyInt())).thenReturn(List.of());

        ListAuditEntries.Query query = new ListAuditEntries.Query(sameTime, sameTime, null, null, null, null, null, null);
        ListAuditEntries.Result result = useCase.handle(query);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void targetIdWithoutTargetTypeThrowsFilterInvalidException() {
        // targetId phải luôn đi kèm targetType
        ListAuditEntries.Query query = new ListAuditEntries.Query(null, null, null, null, null, "target-id-123", null, null);

        assertThatThrownBy(() -> useCase.handle(query))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_FILTER_INVALID));

        verifyNoInteractions(repository);
    }

    @Test
    void invalidCursorThrowsCursorInvalidException() {
        // Con trỏ bị hỏng định dạng ném lỗi AUDIT_CURSOR_INVALID
        ListAuditEntries.Query query = new ListAuditEntries.Query(null, null, null, null, null, null, null, "invalid-cursor-!!!");

        assertThatThrownBy(() -> useCase.handle(query))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_CURSOR_INVALID));

        verifyNoInteractions(repository);
    }

    @Test
    void repositoryReturnsLimitPlusOneItemsThenItemsHasSizeLimitAndNextCursorMatchesLastItem() {
        // Trả về limit+1 bản ghi biểu thị vẫn còn trang sau; nextCursor trỏ tới bản ghi cuối cùng của trang
        int limit = 2;
        Instant now = Instant.parse("2026-10-25T12:00:00Z");
        AuditEntry entry1 = createEntry(now, UUID.randomUUID());
        AuditEntry entry2 = createEntry(now.minusSeconds(1), UUID.randomUUID());
        AuditEntry entry3 = createEntry(now.minusSeconds(2), UUID.randomUUID());

        when(repository.search(any(), isNull(), eq(limit + 1)))
                .thenReturn(new ArrayList<>(List.of(entry1, entry2, entry3)));

        ListAuditEntries.Query query = new ListAuditEntries.Query(null, null, null, null, null, null, limit, null);
        ListAuditEntries.Result result = useCase.handle(query);

        assertThat(result.items()).containsExactly(entry1, entry2);
        assertThat(result.nextCursor()).isNotNull();

        AuditCursor decodedCursor = AuditCursor.decode(result.nextCursor());
        assertThat(decodedCursor.occurredAt()).isEqualTo(entry2.occurredAt());
        assertThat(decodedCursor.id()).isEqualTo(entry2.id());
    }

    @Test
    void repositoryReturnsExactLimitItemsThenNextCursorIsNull() {
        // Trả về đúng limit bản ghi nghĩa là đã hết dữ liệu; nextCursor phải là null
        int limit = 2;
        Instant now = Instant.parse("2026-10-25T12:00:00Z");
        AuditEntry entry1 = createEntry(now, UUID.randomUUID());
        AuditEntry entry2 = createEntry(now.minusSeconds(1), UUID.randomUUID());

        when(repository.search(any(), isNull(), eq(limit + 1)))
                .thenReturn(new ArrayList<>(List.of(entry1, entry2)));

        ListAuditEntries.Query query = new ListAuditEntries.Query(null, null, null, null, null, null, limit, null);
        ListAuditEntries.Result result = useCase.handle(query);

        assertThat(result.items()).containsExactly(entry1, entry2);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void emptyStringParametersAreTreatedAsNullFilter() {
        // Chuỗi rỗng hoặc chỉ có khoảng trắng được coi như không có bộ lọc (null)
        when(repository.search(any(), isNull(), anyInt())).thenReturn(List.of());

        ListAuditEntries.Query query = new ListAuditEntries.Query(null, null, null, "   ", "   ", "   ", null, "   ");
        useCase.handle(query);

        ArgumentCaptor<AuditEntryFilter> filterCaptor = ArgumentCaptor.forClass(AuditEntryFilter.class);
        verify(repository).search(filterCaptor.capture(), isNull(), eq(51));

        AuditEntryFilter filter = filterCaptor.getValue();
        assertThat(filter.action()).isNull();
        assertThat(filter.targetType()).isNull();
        assertThat(filter.targetId()).isNull();
    }
}
