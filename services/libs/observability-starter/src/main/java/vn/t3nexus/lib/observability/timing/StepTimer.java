package vn.t3nexus.lib.observability.timing;

import org.slf4j.Logger;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Đo thời gian từng bước của một thao tác để chẩn đoán phần chạy chậm (xem {@code docs/global/4.convention/logging.md} mục 4).
 * Ghi ở mức DEBUG; DEBUG được kiểm một lần lúc {@link #start}, nếu tắt thì mọi lời gọi không làm gì (không đọc đồng hồ, không tạo chuỗi).
 * Dùng trong MỘT luồng; bước chạy song song thì mỗi tác vụ có timer riêng.
 *
 * <pre>{@code
 * try (var timer = StepTimer.start(log, "ExportReport")) {
 *     var rows = loadRows(query);
 *     timer.step("load-rows", rows.size());             // dạng đánh dấu: đặt SAU bước vừa xong
 *     var file = timer.step("build-excel", () -> build(rows));   // dạng bọc khối: đo chính xác khối
 * }
 * }</pre>
 */
public final class StepTimer implements AutoCloseable {

    private final Logger log;
    private final String operation;
    private final boolean enabled;
    private final LongSupplier clock;
    private final long startNanos;
    private long lastNanos;

    private StepTimer(Logger log, String operation, LongSupplier clock) {
        this.log = log;
        this.operation = operation;
        this.clock = clock;
        this.enabled = log.isDebugEnabled();
        this.startNanos = enabled ? clock.getAsLong() : 0L;
        this.lastNanos = startNanos;
    }

    public static StepTimer start(Logger log, String operation) {
        return new StepTimer(log, operation, System::nanoTime);
    }

    /** Dùng cho test: đồng hồ do người gọi cấp. */
    public static StepTimer start(Logger log, String operation, LongSupplier nanoClock) {
        return new StepTimer(log, operation, nanoClock);
    }

    /** Đánh dấu bước vừa xong: ghi thời gian từ lần đánh dấu trước (hoặc từ lúc bắt đầu). */
    public void step(String name) {
        mark(name, null);
    }

    /** Như {@link #step(String)} kèm kích thước dữ liệu (số dòng, số byte) vì thời gian thường tỷ lệ với nó. */
    public void step(String name, long size) {
        mark(name, size);
    }

    /** Đo chính xác một khối; vẫn ghi thời gian khi khối ném lỗi rồi ném tiếp. */
    public <T> T step(String name, Supplier<T> block) {
        if (!enabled) return block.get();
        lastNanos = clock.getAsLong();
        try {
            return block.get();
        } finally {
            mark(name, null);
        }
    }

    public void step(String name, Runnable block) {
        step(name, () -> {
            block.run();
            return null;
        });
    }

    /** Ghi tổng thời gian. */
    @Override
    public void close() {
        if (!enabled) return;
        long now = clock.getAsLong();
        log.debug("[{}] done total={}ms", operation, millis(now - startNanos));
    }

    private void mark(String name, Long size) {
        if (!enabled) return;
        long now = clock.getAsLong();
        long took = now - lastNanos;
        lastNanos = now;
        if (size == null) {
            log.debug("[{}] step={} took={}ms total={}ms", operation, name, millis(took), millis(now - startNanos));
        } else {
            log.debug("[{}] step={} took={}ms total={}ms size={}", operation, name, millis(took), millis(now - startNanos), size);
        }
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }
}
