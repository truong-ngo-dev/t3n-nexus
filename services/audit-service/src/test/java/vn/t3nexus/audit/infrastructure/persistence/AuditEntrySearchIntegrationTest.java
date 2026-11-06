package vn.t3nexus.audit.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.t3nexus.audit.AuditPostgresTestBase;
import vn.t3nexus.audit.AuditServiceApplication;
import vn.t3nexus.audit.application.auditentry.AuditCursor;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntryId;
import vn.t3nexus.audit.application.auditentry.AuditEntryFilter;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditTarget;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuditServiceApplication.class, properties = {
        "spring.main.web-application-type=none",
        "spring.kafka.listener.auto-startup=false"
})
class AuditEntrySearchIntegrationTest extends AuditPostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    private AuditEntryRepositoryAdapter repository;

    @Autowired
    private AuditEntryQueryAdapter query;

    private AuditEntry createAndInsert(UUID actorId, String action, String targetType, String targetId, Instant occurredAt, UUID id) {
        AuditEntry entry = AuditEntry.restore(
                id,
                "ev-" + UUID.randomUUID(),
                occurredAt,
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                new AuditActor(actorId, "ADMIN"),
                action,
                new AuditTarget(targetType, targetId),
                Map.of("key", "val"),
                "test-service",
                "trace-" + UUID.randomUUID()
        );
        boolean inserted = repository.insertIfAbsent(entry);
        assertThat(inserted).isTrue();
        return entry;
    }

    @Test
    void kt09_filterByIndividualAndCombinedCriteria() {
        // KT-09: Mỗi ca tạo dữ liệu riêng với targetType và actorId duy nhất để cách ly với các test khác
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        String actionA = "ACTION_A";
        String actionB = "ACTION_B";
        Instant baseTime = Instant.parse("2026-10-25T10:00:00Z");

        AuditEntry e1 = createAndInsert(actorId, actionA, targetType, "id-1", baseTime.plusSeconds(10), UUID.randomUUID());
        AuditEntry e2 = createAndInsert(actorId, actionB, targetType, "id-2", baseTime.plusSeconds(20), UUID.randomUUID());
        AuditEntry e3 = createAndInsert(UUID.randomUUID(), actionA, targetType, "id-1", baseTime.plusSeconds(30), UUID.randomUUID());

        // Lọc riêng theo actorId
        AuditEntryFilter filterActor = new AuditEntryFilter(null, null, actorId, null, targetType, null);
        List<AuditEntry> resActor = query.search(filterActor, null, 10);
        assertThat(resActor).extracting(AuditEntry::id).containsExactlyInAnyOrder(e1.id(), e2.id());

        // Lọc riêng theo action
        AuditEntryFilter filterAction = new AuditEntryFilter(null, null, null, actionA, targetType, null);
        List<AuditEntry> resAction = query.search(filterAction, null, 10);
        assertThat(resAction).extracting(AuditEntry::id).containsExactlyInAnyOrder(e1.id(), e3.id());

        // Lọc riêng theo targetType
        AuditEntryFilter filterTargetType = new AuditEntryFilter(null, null, null, null, targetType, null);
        List<AuditEntry> resTargetType = query.search(filterTargetType, null, 10);
        assertThat(resTargetType).extracting(AuditEntry::id).containsExactlyInAnyOrder(e1.id(), e2.id(), e3.id());

        // Lọc theo targetType + targetId
        AuditEntryFilter filterTarget = new AuditEntryFilter(null, null, null, null, targetType, "id-1");
        List<AuditEntry> resTarget = query.search(filterTarget, null, 10);
        assertThat(resTarget).extracting(AuditEntry::id).containsExactlyInAnyOrder(e1.id(), e3.id());

        // Kết hợp: actorId + action + targetType + targetId
        AuditEntryFilter filterCombined = new AuditEntryFilter(null, null, actorId, actionA, targetType, "id-1");
        List<AuditEntry> resCombined = query.search(filterCombined, null, 10);
        assertThat(resCombined).extracting(AuditEntry::id).containsExactly(e1.id());

        // Không khớp kết quả trả về danh sách rỗng
        AuditEntryFilter filterNoMatch = new AuditEntryFilter(null, null, actorId, "NON_EXISTENT_ACTION", targetType, null);
        List<AuditEntry> resEmpty = query.search(filterNoMatch, null, 10);
        assertThat(resEmpty).isEmpty();
    }

    @Test
    void kt09_timeRangeFromInclusiveAndToExclusive() {
        // from gồm (>=) và to không gồm (<)
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        Instant tFrom = Instant.parse("2026-10-25T12:00:00Z");
        Instant tBetween = Instant.parse("2026-10-25T12:30:00Z");
        Instant tTo = Instant.parse("2026-10-25T13:00:00Z");

        AuditEntry eFrom = createAndInsert(actorId, "ACT", targetType, "1", tFrom, UUID.randomUUID());
        AuditEntry eMid = createAndInsert(actorId, "ACT", targetType, "2", tBetween, UUID.randomUUID());
        AuditEntry eTo = createAndInsert(actorId, "ACT", targetType, "3", tTo, UUID.randomUUID());

        AuditEntryFilter filter = new AuditEntryFilter(tFrom, tTo, actorId, null, targetType, null);
        List<AuditEntry> results = query.search(filter, null, 10);

        // eFrom có mặt tại from, eTo vắng mặt tại to
        assertThat(results).extracting(AuditEntry::id).contains(eFrom.id(), eMid.id());
        assertThat(results).extracting(AuditEntry::id).doesNotContain(eTo.id());
    }

    @Test
    void kt09_findByIdPresentAndAbsent() {
        // findById tìm thấy bản ghi tồn tại và trả Optional.empty cho id chưa có
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        UUID id = UUID.randomUUID();
        AuditEntry created = createAndInsert(actorId, "FIND_ME", targetType, "1", Instant.now(), id);

        Optional<AuditEntry> found = repository.findById(AuditEntryId.of(id));
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(id);
        assertThat(found.get().action()).isEqualTo("FIND_ME");

        Optional<AuditEntry> notFound = repository.findById(AuditEntryId.of(UUID.randomUUID()));
        assertThat(notFound).isEmpty();
    }

    @Test
    void kt10_orderAndCursorPaginationAcross7Entries() {
        // KT-10: 7 bản ghi, ba cái có cùng occurredAt và id biết trước
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();

        Instant t1 = Instant.parse("2026-10-25T10:00:00Z");
        Instant tSame = Instant.parse("2026-10-25T11:00:00Z");
        Instant t3 = Instant.parse("2026-10-25T12:00:00Z");
        Instant t4 = Instant.parse("2026-10-25T13:00:00Z");
        Instant t5 = Instant.parse("2026-10-25T14:00:00Z");

        // 3 id có thứ tự giảm dần rõ ràng
        UUID idSameHigh = UUID.fromString("ffffffff-0000-0000-0000-000000000003");
        UUID idSameMid = UUID.fromString("ffffffff-0000-0000-0000-000000000002");
        UUID idSameLow = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

        AuditEntry e1 = createAndInsert(actorId, "ACT", targetType, "1", t1, UUID.randomUUID());
        AuditEntry eSameLow = createAndInsert(actorId, "ACT", targetType, "2", tSame, idSameLow);
        AuditEntry eSameMid = createAndInsert(actorId, "ACT", targetType, "3", tSame, idSameMid);
        AuditEntry eSameHigh = createAndInsert(actorId, "ACT", targetType, "4", tSame, idSameHigh);
        AuditEntry e3 = createAndInsert(actorId, "ACT", targetType, "5", t3, UUID.randomUUID());
        AuditEntry e4 = createAndInsert(actorId, "ACT", targetType, "6", t4, UUID.randomUUID());
        AuditEntry e5 = createAndInsert(actorId, "ACT", targetType, "7", t5, UUID.randomUUID());

        // Danh sách kỳ vọng sắp xếp theo (occurredAt giảm dần, id giảm dần)
        List<AuditEntry> expectedOrder = List.of(e5, e4, e3, eSameHigh, eSameMid, eSameLow, e1);

        AuditEntryFilter filter = new AuditEntryFilter(null, null, actorId, null, targetType, null);

        // Duyệt bằng limit = 1
        List<AuditEntry> collectedLimit1 = fetchAllWithCursor(filter, 1);
        assertThat(collectedLimit1).extracting(AuditEntry::id).containsExactlyElementsOf(expectedOrder.stream().map(AuditEntry::id).toList());

        // Duyệt bằng limit = 2
        List<AuditEntry> collectedLimit2 = fetchAllWithCursor(filter, 2);
        assertThat(collectedLimit2).extracting(AuditEntry::id).containsExactlyElementsOf(expectedOrder.stream().map(AuditEntry::id).toList());

        // Duyệt bằng limit = 7
        List<AuditEntry> collectedLimit7 = fetchAllWithCursor(filter, 7);
        assertThat(collectedLimit7).extracting(AuditEntry::id).containsExactlyElementsOf(expectedOrder.stream().map(AuditEntry::id).toList());
    }

    @Test
    void kt10_insertingNewerRecordBetweenCursorCallsDoesNotCauseDuplicatesOrMissing() {
        // Chèn thêm một bản ghi MỚI HƠN giữa hai lần gọi không làm lặp hay sót phần còn lại
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();

        Instant t1 = Instant.parse("2026-10-25T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-25T11:00:00Z");
        Instant t3 = Instant.parse("2026-10-25T12:00:00Z");
        Instant t4 = Instant.parse("2026-10-25T13:00:00Z");

        AuditEntry e1 = createAndInsert(actorId, "ACT", targetType, "1", t1, UUID.randomUUID());
        AuditEntry e2 = createAndInsert(actorId, "ACT", targetType, "2", t2, UUID.randomUUID());
        AuditEntry e3 = createAndInsert(actorId, "ACT", targetType, "3", t3, UUID.randomUUID());
        AuditEntry e4 = createAndInsert(actorId, "ACT", targetType, "4", t4, UUID.randomUUID());

        AuditEntryFilter filter = new AuditEntryFilter(null, null, actorId, null, targetType, null);

        // Lấy trang 1 cỡ 2: được e4, e3
        List<AuditEntry> page1 = query.search(filter, null, 2);
        assertThat(page1).extracting(AuditEntry::id).containsExactly(e4.id(), e3.id());
        AuditCursor cursor = AuditCursor.of(page1.get(page1.size() - 1));

        // Chèn thêm một bản ghi mới hơn hẳn (t5 > t4)
        Instant t5 = Instant.parse("2026-10-25T14:00:00Z");
        createAndInsert(actorId, "ACT", targetType, "5", t5, UUID.randomUUID());

        // Lấy trang 2 với con trỏ cũ: phải tiếp tục ra đúng e2, e1 mà không bị xáo trộn, không sót, không trùng
        List<AuditEntry> page2 = query.search(filter, cursor, 2);
        assertThat(page2).extracting(AuditEntry::id).containsExactly(e2.id(), e1.id());
    }

    @Test
    void kt10_tieBreakerOrderIsStableWhenSameOccurredAt() {
        // Thứ tự phụ theo id giảm dần khi cùng occurredAt
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        Instant sameTime = Instant.parse("2026-10-25T15:00:00Z");

        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        AuditEntry e1 = createAndInsert(actorId, "ACT", targetType, "1", sameTime, id1);
        AuditEntry e2 = createAndInsert(actorId, "ACT", targetType, "2", sameTime, id2);
        AuditEntry e3 = createAndInsert(actorId, "ACT", targetType, "3", sameTime, id3);

        AuditEntryFilter filter = new AuditEntryFilter(null, null, actorId, null, targetType, null);
        List<AuditEntry> list = query.search(filter, null, 10);

        assertThat(list).extracting(AuditEntry::id).containsExactly(e3.id(), e2.id(), e1.id());
    }

    @Test
    void kt10_orderingIsDefinedBySqlNotByIndexChoice() {
        // Thứ tự phụ theo id phải do ORDER BY bảo đảm, không do chỉ mục "tình cờ" trả đúng. Tắt quét chỉ mục trong giao dịch này
        // để kế hoạch phải sắp xếp thật; các dòng cùng occurredAt được ghi theo id tăng dần nên nếu thiếu tie-break sẽ ra ngược.
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        Instant same = Instant.parse("2026-10-25T11:00:00Z");
        // Ba id chung phần cao ngẫu nhiên (bảng không xóa được nên id phải duy nhất toàn lớp), khác nhau ở phần thấp 1 < 2 < 3
        long hi = java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0x7FFF_FFFF_FFFF_0000L;
        AuditEntry low = createAndInsert(actorId, "ACT", targetType, "1", same, new UUID(hi, 1L));
        AuditEntry mid = createAndInsert(actorId, "ACT", targetType, "2", same, new UUID(hi, 2L));
        AuditEntry high = createAndInsert(actorId, "ACT", targetType, "3", same, new UUID(hi, 3L));
        AuditEntryFilter filter = new AuditEntryFilter(null, null, actorId, null, targetType, null);

        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL enable_indexscan = off");
            jdbc.execute("SET LOCAL enable_indexonlyscan = off");
            jdbc.execute("SET LOCAL enable_bitmapscan = off");

            assertThat(query.search(filter, null, 10)).extracting(AuditEntry::id)
                    .containsExactly(high.id(), mid.id(), low.id());
            // Và duyệt theo con trỏ cỡ 1 vẫn ra đúng thứ tự, không lặp, không sót
            assertThat(fetchAllWithCursor(filter, 1)).extracting(AuditEntry::id)
                    .containsExactly(high.id(), mid.id(), low.id());
        });
    }

    private List<AuditEntry> fetchAllWithCursor(AuditEntryFilter filter, int limit) {
        List<AuditEntry> collected = new ArrayList<>();
        AuditCursor cursor = null;
        int calls = 0;
        while (true) {
            // Trần số lần gọi: con trỏ sai (không tiến) sẽ lặp mãi, phải đỏ chứ không treo
            if (++calls > 100) {
                throw new AssertionError("Duyệt trang không kết thúc sau 100 lần gọi: con trỏ không tiến");
            }
            List<AuditEntry> page = query.search(filter, cursor, limit);
            if (page.isEmpty()) {
                break;
            }
            collected.addAll(page);
            if (page.size() < limit) {
                break;
            }
            cursor = AuditCursor.of(page.get(page.size() - 1));
        }
        return collected;
    }
}
