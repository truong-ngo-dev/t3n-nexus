package vn.t3nexus.apigateway.support;

import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Một Redis thật (Testcontainers) dùng chung cho mọi test trong một lần chạy: khởi động một lần, Ryuk dọn khi JVM thoát. Giới hạn
 * tần suất dùng Lua trên Redis thật nên không giả bằng Redis nhúng. Test dùng {@link #flush()} ở {@code @BeforeEach} để không
 * chia sẻ bộ đếm giữa các test. Không chạm Redis cục bộ của người dùng.
 */
public final class RedisContainerHolder {

    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    private RedisContainerHolder() {
    }

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(6379);
    }

    /** Các khóa khớp mẫu (rỗng nếu không có). */
    public static java.util.List<String> keys(String pattern) {
        try {
            String out = REDIS.execInContainer("redis-cli", "--scan", "--pattern", pattern).getStdout();
            return out.isBlank() ? java.util.List.of() : java.util.Arrays.asList(out.strip().split("\\R"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Xóa mọi khóa (bộ đếm giới hạn tần suất). */
    public static void flush() {
        try {
            REDIS.execInContainer("redis-cli", "flushall");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
