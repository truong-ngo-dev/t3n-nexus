package vn.t3nexus.lib.common.domain.service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Cài đặt {@link IdGenerator} theo RFC 9562 mục 5.7 (UUID v7).
 * <br>Bố cục: 48 bit mili-giây | phiên bản 7 | 12 bit bộ đếm | biến thể 10 | 62 bit ngẫu nhiên.
 * <br>Đơn điệu trong một thể hiện: cùng một mili-giây thì bộ đếm tăng dần; tràn bộ đếm thì mượn mili-giây kế tiếp;
 * đồng hồ lùi thì giữ mili-giây đã dùng. Giữa nhiều thể hiện chỉ gần đúng thứ tự (ADR-0001).
 */
public class UuidV7IdGenerator implements IdGenerator {

    private static final int COUNTER_MAX = 0xFFF;

    private final LongSupplier clockMillis;
    private final SecureRandom random = new SecureRandom();

    private long lastMillis = -1;
    private int  counter;

    public UuidV7IdGenerator() {
        this(System::currentTimeMillis);
    }

    public UuidV7IdGenerator(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    @Override
    public synchronized UUID generate() {
        long now = clockMillis.getAsLong();
        if (now > lastMillis) {
            lastMillis = now;
            // Bắt đầu ở nửa dưới để còn chỗ tăng trong cùng mili-giây
            counter = random.nextInt(COUNTER_MAX / 2);
        } else if (counter >= COUNTER_MAX) {
            lastMillis++;
            counter = random.nextInt(COUNTER_MAX / 2);
        } else {
            counter++;
        }

        long msb = (lastMillis << 16) | (0x7L << 12) | counter;
        long lsb = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }
}
