package vn.t3nexus.oauth2.presentation.user_account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-13 (bước 10): nhiều yêu cầu song song cùng một email thì đúng một yêu cầu thành công (201), các yêu cầu còn lại 409
 * {@code EMAIL_TAKEN}, và cơ sở dữ liệu chỉ có một tài khoản. Không có 500.
 * <br>Mỗi vòng dùng một email mới và bắt đầu cùng lúc nhờ một rào chắn. Số vòng lấy từ {@code -Dconcurrency.rounds}
 * (mặc định 10 cho lần chạy thường; kế hoạch yêu cầu chạy 100 vòng một lần với {@code -Dconcurrency.rounds=100}).
 * <br>Mỗi yêu cầu hợp lệ băm một lần (~80 ms CPU) nên 100 vòng × 100 yêu cầu mất vài phút. Dữ liệu test bị xóa sau mỗi vòng.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserAccountConcurrencyTest {

    private static final int PARALLEL_REQUESTS = 100;
    private static final int ROUNDS = Integer.getInteger("concurrency.rounds", 10);

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @Test
    void parallel_requests_with_the_same_email_create_exactly_one_account() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                String email = "api-test-race-" + UUID.randomUUID() + "@example.com";
                try {
                    List<Integer> statuses = raceOnce(pool, email);

                    long created  = statuses.stream().filter(s -> s == 201).count();
                    long conflict = statuses.stream().filter(s -> s == 409).count();
                    assertThat(created).as("vòng %d: số 201, tất cả mã = %s", round, statuses).isEqualTo(1);
                    assertThat(conflict).as("vòng %d: số 409, tất cả mã = %s", round, statuses)
                            .isEqualTo(PARALLEL_REQUESTS - 1);
                    assertThat(jdbc.queryForObject("select count(*) from user_accounts where email = ?", Integer.class, email))
                            .as("vòng %d: số tài khoản", round).isEqualTo(1);
                } finally {
                    jdbc.update("delete from user_accounts where email = ?", email);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Integer> raceOnce(ExecutorService pool, String email) throws Exception {
        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch go    = new CountDownLatch(1);
        String body = "{\"email\":\"" + email + "\",\"password\":\"Matkhau123\"}";

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            futures.add(pool.submit(() -> {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/register"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                ready.countDown();
                go.await();
                return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            }));
        }
        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(180, TimeUnit.SECONDS));
        }
        return statuses;
    }
}
