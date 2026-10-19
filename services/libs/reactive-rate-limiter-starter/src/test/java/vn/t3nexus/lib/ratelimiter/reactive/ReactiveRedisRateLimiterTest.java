package vn.t3nexus.lib.ratelimiter.reactive;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bộ đếm cửa sổ trượt trên Redis thật (feature 06, nhóm A, `tech.md` T2, T3): khi từ chối trả đúng thời gian còn lại đến khi giải phóng
 * một lượt (để {@code Retry-After} không sai), yêu cầu bị từ chối không ghi lượt nào, và Redis lỗi hoặc không trả lời thì mở cửa trong
 * thời gian chờ ngắn thay vì treo yêu cầu.
 */
class ReactiveRedisRateLimiterTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static ReactiveStringRedisTemplate redis;
    private static final List<LettuceConnectionFactory> FACTORIES = new ArrayList<>();

    @BeforeAll
    static void start() {
        REDIS.start();
        redis = template(REDIS.getHost(), REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stop() {
        FACTORIES.forEach(LettuceConnectionFactory::destroy);
        REDIS.stop();
    }

    private static ReactiveStringRedisTemplate template(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        FACTORIES.add(factory);
        return new ReactiveStringRedisTemplate((ReactiveRedisConnectionFactory) factory);
    }

    private static String newKey() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    void allows_up_to_the_limit_then_denies_with_the_time_until_the_oldest_hit_leaves_the_window() throws Exception {
        ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(redis, Duration.ofSeconds(5));   // rộng để lần gọi đầu (nạp Lua, máy bận) không bị mở cửa nhầm
        String key = newKey();
        Duration window = Duration.ofSeconds(4);

        for (int i = 0; i < 3; i++) {
            RateLimitDecision allowed = limiter.acquire(key, 3, window).block();
            assertThat(allowed.allowed()).as("lượt %d", i + 1).isTrue();
            assertThat(allowed.retryAfter()).isEqualTo(Duration.ZERO);
        }

        RateLimitDecision denied = limiter.acquire(key, 3, window).block();
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isPositive().isLessThanOrEqualTo(window);
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 4L);

        Thread.sleep(1200);
        RateLimitDecision later = limiter.acquire(key, 3, window).block();
        assertThat(later.allowed()).isFalse();
        assertThat(later.retryAfter()).as("thời gian chờ giảm theo thời gian").isLessThan(denied.retryAfter());

        Thread.sleep(window.toMillis());
        assertThat(limiter.acquire(key, 3, window).block().allowed()).as("hết cửa sổ thì được lại").isTrue();
    }

    @Test
    void a_denied_request_records_nothing() {
        ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(redis, Duration.ofSeconds(5));   // rộng để lần gọi đầu (nạp Lua, máy bận) không bị mở cửa nhầm
        String key = newKey();

        for (int i = 0; i < 2; i++) limiter.acquire(key, 2, Duration.ofSeconds(30)).block();
        for (int i = 0; i < 5; i++) assertThat(limiter.acquire(key, 2, Duration.ofSeconds(30)).block().allowed()).isFalse();

        assertThat(redis.opsForZSet().size(key).block()).as("chỉ hai lượt đã cho qua nằm trong bộ đếm").isEqualTo(2L);
    }

    @Test
    void the_boolean_method_keeps_working() {
        ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(redis, Duration.ofSeconds(5));   // rộng để lần gọi đầu (nạp Lua, máy bận) không bị mở cửa nhầm
        String key = newKey();

        assertThat(limiter.tryAcquire(key, 1, Duration.ofSeconds(30)).block()).isTrue();
        assertThat(limiter.tryAcquire(key, 1, Duration.ofSeconds(30)).block()).isFalse();
    }

    @Test
    void a_redis_that_never_answers_fails_open_within_the_timeout() throws IOException {
        try (ServerSocket silent = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket client = silent.accept()) {
                    Thread.sleep(10_000);              // nhận kết nối rồi im lặng
                } catch (Exception ignored) {
                    // đóng khi test xong
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(template("127.0.0.1", silent.getLocalPort()), Duration.ofMillis(300));

            long start = System.nanoTime();
            RateLimitDecision decision = limiter.acquire(newKey(), 1, Duration.ofSeconds(30)).block(Duration.ofSeconds(8));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertThat(decision.allowed()).as("Redis không trả lời thì mở cửa").isTrue();
            assertThat(elapsedMillis).as("không chờ quá thời gian chờ ngắn").isLessThan(2_000);
        }
    }

    @Test
    void an_unreachable_redis_fails_open() {
        ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(template("127.0.0.1", 1), Duration.ofMillis(300));

        assertThat(limiter.acquire(newKey(), 1, Duration.ofSeconds(30)).block(Duration.ofSeconds(8)).allowed()).isTrue();
    }

    /**
     * Redis chậm kết nối hơn thời gian chờ của bộ đếm (gặp ở lần gọi đầu sau khi khởi động): bộ đếm mở cửa đúng hạn, rồi lần kết nối bị bỏ dở báo
     * lỗi muộn. Lỗi muộn đó không được thành dòng ERROR "onErrorDropped" của Reactor (cảnh báo đã có một dòng WARN là đủ).
     */
    @Test
    void a_late_connection_failure_after_the_fail_open_does_not_log_an_error() throws Exception {
        var operators = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("reactor.core.publisher.Operators");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        operators.addAppender(appender);
        try {
            var config = org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(600)).build();
            var factory = new LettuceConnectionFactory(new org.springframework.data.redis.connection.RedisStandaloneConfiguration("10.255.255.1", 6379), config);
            factory.afterPropertiesSet();
            FACTORIES.add(factory);
            var slow = new ReactiveStringRedisTemplate((ReactiveRedisConnectionFactory) factory);

            RateLimitDecision decision = new ReactiveRedisRateLimiter(slow, Duration.ofMillis(200))
                    .acquire(newKey(), 1, Duration.ofSeconds(30)).block(Duration.ofSeconds(8));
            assertThat(decision.allowed()).isTrue();

            Thread.sleep(3_000);       // chờ lần kết nối bị bỏ dở báo lỗi muộn
            assertThat(appender.list).as("không có dòng ERROR từ Reactor").noneMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.ERROR);
        } finally {
            operators.detachAppender(appender);
        }
    }
}
