package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-15 (feature 06, nhóm C, R11, `tech.md` T5): nhiều yêu cầu đồng thời của cùng một phiên gặp token hết hạn chỉ gây <b>một</b> lần làm mới
 * (Xác thực đã thử: hai làm mới cùng mã làm mới đều thành công nhưng chỉ một mã mới dùng được, nên làm mới thừa là đăng xuất oan). Xác thực
 * giả trả chậm 400 ms để các yêu cầu chồng lên nhau; tất cả phải nhận 200 và token mới.
 */
class ConcurrentRefreshTest extends GatewayTestBase {

    private static final int REQUESTS = 6;

    @Test
    void concurrent_requests_with_an_expired_token_cause_exactly_one_refresh_and_all_succeed() throws Exception {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER", 1);
        respondToRefreshWithNewTokens("user-1", sid, "USER", "second");
        OAUTH2.delayResponses(400);
        CUSTOMER.respond("GET", "/api/me", 200, "application/json", "{}");

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                calls.add(() -> client().get().uri("/api/customer/me").header("Cookie", "STOREFRONT_SESSION=" + login.sessionCookie())
                        .exchange().returnResult(Void.class).getStatus().value());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) statuses.add(f.get());

            assertThat(statuses).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(refreshRequests()).hasSize(1);
        assertThat(CUSTOMER.requests()).hasSize(REQUESTS);
        assertThat(CUSTOMER.requests()).allSatisfy(r ->
                assertThat(new String(java.util.Base64.getUrlDecoder().decode(r.header("Authorization").split("\\.")[1]))).contains("\"marker\":\"second\""));

        OAUTH2.delayResponses(0);
        client().get().uri("/api/customer/me").header("Cookie", "STOREFRONT_SESSION=" + login.sessionCookie()).exchange().expectStatus().isOk();
        assertThat(refreshRequests()).as("token mới đã lưu vào phiên, không làm mới lại").hasSize(1);
    }
}
