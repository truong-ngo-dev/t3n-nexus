package vn.t3nexus.lib.common.domain.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7IdGeneratorTest {

    @Test
    void sets_version_7_and_rfc_variant() {
        UUID id = new UuidV7IdGenerator().generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void carries_the_clock_milliseconds_in_the_first_48_bits() {
        long millis = 1_760_000_000_123L;

        UUID id = new UuidV7IdGenerator(() -> millis).generate();

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void is_strictly_increasing_within_the_same_millisecond() {
        UuidV7IdGenerator generator = new UuidV7IdGenerator(() -> 1_760_000_000_000L);

        UUID previous = generator.generate();
        for (int i = 0; i < 3000; i++) {
            UUID next = generator.generate();
            assertThat(compare(next, previous)).isPositive();
            previous = next;
        }
    }

    @Test
    void keeps_increasing_when_the_clock_goes_backwards() {
        long[] now = {1_760_000_000_500L};
        UuidV7IdGenerator generator = new UuidV7IdGenerator(() -> now[0]);

        UUID first = generator.generate();
        now[0] = 1_760_000_000_000L;
        UUID second = generator.generate();

        assertThat(compare(second, first)).isPositive();
    }

    @Test
    void borrows_the_next_millisecond_when_the_counter_overflows() {
        UuidV7IdGenerator generator = new UuidV7IdGenerator(() -> 1_760_000_000_000L);

        Set<UUID> seen = new HashSet<>();
        UUID previous = generator.generate();
        for (int i = 0; i < 10_000; i++) {
            UUID next = generator.generate();
            assertThat(compare(next, previous)).isPositive();
            assertThat(seen.add(next)).isTrue();
            previous = next;
        }
    }

    /** Thứ tự byte không dấu, như cách kiểu uuid của PostgreSQL so sánh. */
    private static int compare(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return msb != 0 ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
