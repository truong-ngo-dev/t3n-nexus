package vn.t3nexus.lib.eventconsumer.testing;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.events.EventEnvelope;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bộ khung kiểm thử khử trùng phải phân biệt được handler đúng và sai (kiểm bằng chính các handler mẫu đúng và sai). */
class DeliveryHarnessTest {

    private static EventEnvelope event(String id, int version, String value) {
        return new EventEnvelope(id, "agg-1", "Probe", "ProbeChanged", Instant.parse("2026-10-24T00:00:00Z"), null, null, 1,
                Map.of("version", version, "value", value));
    }

    /** Mỗi event tạo một bản ghi, chặn bằng mã event (như ràng buộc duy nhất). */
    static class UniqueRecordHandler {
        final Set<String> rows = new HashSet<>();
        void handle(EventEnvelope e) { rows.add(e.eventId()); }
        void reset() { rows.clear(); }
    }

    /** Đếm mỗi lần nhận: sai, vì lần hai làm đổi kết quả. */
    static class CountingHandler {
        final AtomicInteger count = new AtomicInteger();
        void handle(EventEnvelope e) { count.incrementAndGet(); }
        void reset() { count.set(0); }
    }

    /** Ghi giá trị mới nhất, bất kể phiên bản: sai khi đảo thứ tự. */
    static class LastWriteWinsHandler {
        final Map<String, String> state = new HashMap<>();
        void handle(EventEnvelope e) { state.put(e.aggregateId(), (String) ((Map<?, ?>) e.payload()).get("value")); }
        void reset() { state.clear(); }
    }

    /** Chỉ nhận khi phiên bản lớn hơn phiên bản đang giữ: đúng với trùng và đảo thứ tự. */
    static class VersionGuardedHandler {
        final Map<String, Integer> versions = new HashMap<>();
        final Map<String, String> state = new HashMap<>();
        void handle(EventEnvelope e) {
            Map<?, ?> p = (Map<?, ?>) e.payload();
            int v = (Integer) p.get("version");
            if (v > versions.getOrDefault(e.aggregateId(), 0)) {
                versions.put(e.aggregateId(), v);
                state.put(e.aggregateId(), (String) p.get("value"));
            }
        }
        void reset() { versions.clear(); state.clear(); }
    }

    @Test
    void duplicateDeliveryOfAUniqueRecordHandlerIsHarmless() {
        UniqueRecordHandler h = new UniqueRecordHandler();

        new DeliveryHarness<>(h::handle, () -> Set.copyOf(h.rows), h::reset)
                .assertDuplicateDeliveryIsHarmless(event("e-1", 1, "a"));
    }

    @Test
    void duplicateDeliveryOfACountingHandlerIsReported() {
        CountingHandler h = new CountingHandler();

        assertThatThrownBy(() -> new DeliveryHarness<>(h::handle, h.count::get, h::reset)
                .assertDuplicateDeliveryIsHarmless(event("e-1", 1, "a")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("trùng");
    }

    @Test
    void reorderingIsReportedForALastWriteWinsHandler() {
        LastWriteWinsHandler h = new LastWriteWinsHandler();

        assertThatThrownBy(() -> new DeliveryHarness<>(h::handle, () -> Map.copyOf(h.state), h::reset)
                .assertReorderingIsHarmless(event("e-1", 1, "old"), event("e-2", 2, "new")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("thứ tự");
    }

    @Test
    void reorderingAndDuplicationAreHarmlessForAVersionGuardedHandler() {
        VersionGuardedHandler h = new VersionGuardedHandler();
        DeliveryHarness<Map<String, String>> harness = new DeliveryHarness<>(h::handle, () -> Map.copyOf(h.state), h::reset);

        harness.assertReorderingIsHarmless(event("e-1", 1, "old"), event("e-2", 2, "new"));
        harness.assertDuplicateDeliveryIsHarmless(event("e-2", 2, "new"));
        assertThat(h.state).isEmpty();      // đã reset sau lần kiểm cuối: bộ khung không để lại trạng thái
    }

    @Test
    void theHarnessResetsStateBeforeEachScenario() {
        UniqueRecordHandler h = new UniqueRecordHandler();
        h.rows.add("leftover");

        new DeliveryHarness<>(h::handle, () -> Set.copyOf(h.rows), h::reset)
                .assertDuplicateDeliveryIsHarmless(event("e-1", 1, "a"));

        assertThat(h.rows).doesNotContain("leftover");
    }
}
