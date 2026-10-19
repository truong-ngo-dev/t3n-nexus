package vn.t3nexus.storefrontwebbff.support;

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

    /** Giá trị chuỗi của khóa, hoặc {@code null} nếu không có. */
    public static String get(String key) {
        String out = redis("get", key);
        return out.isEmpty() ? null : out;
    }

    /** Thời gian sống còn lại (giây); -2 nếu khóa không có, -1 nếu không có hạn. */
    public static long ttl(String key) {
        return Long.parseLong(redis("ttl", key));
    }

    /** Tên các trường của một hash. */
    public static java.util.List<String> hkeys(String key) {
        String out = redis("hkeys", key);
        return out.isEmpty() ? java.util.List.of() : java.util.List.of(out.split("\\R"));
    }

    public static void hdel(String key, String field) {
        redis("hdel", key, field);
    }

    public static void expire(String key, long seconds) {
        redis("expire", key, String.valueOf(seconds));
    }

    public static boolean exists(String key) {
        return "1".equals(redis("exists", key));
    }

    /** Danh sách khóa khớp mẫu. */
    public static java.util.List<String> keys(String pattern) {
        String out = redis("keys", pattern);
        return out.isEmpty() ? java.util.List.of() : java.util.List.of(out.split("\\R"));
    }

    private static String redis(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "redis-cli";
        System.arraycopy(args, 0, command, 1, args.length);
        try {
            return REDIS.execInContainer(command).getStdout().strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Số phiên Spring còn chứa client ủy quyền (tức còn token) trong Redis, bất kể mã phiên. Đếm theo nội dung chứ không theo mã cụ thể, vì Spring có
     * thể đổi tên khóa phiên (đổi mã phiên) nên kiểm theo mã cũ sẽ thấy "đã xóa" dù phiên còn đó dưới mã mới.
     */
    public static long sessionsHoldingTokens() {
        return keys("spring:session:sessions:*").stream().filter(k -> !k.contains("expires"))
                .filter(k -> hkeys(k).stream().anyMatch(f -> f.contains("AUTHORIZED_CLIENTS"))).count();
    }
}
