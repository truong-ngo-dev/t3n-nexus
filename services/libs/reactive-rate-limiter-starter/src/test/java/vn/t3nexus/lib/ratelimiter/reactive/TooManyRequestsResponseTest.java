package vn.t3nexus.lib.ratelimiter.reactive;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Phản hồi 429 chung của hai cổng (feature 06, `tech.md` T2): khung lỗi chung với mã chữ ổn định và {@code Retry-After} đúng. */
class TooManyRequestsResponseTest {

    @Test
    void writes_429_with_the_common_error_frame_and_the_remaining_seconds() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/register"));

        TooManyRequestsResponse.write(exchange, RateLimitDecision.deny(Duration.ofMillis(2_300))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("3");
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"success\":false", "\"code\":\"TOO_MANY_REQUESTS\"", "\"message\":\"");
    }

    @Test
    void retry_after_is_at_least_one_second() {
        assertThat(RateLimitDecision.deny(Duration.ofMillis(5)).retryAfterSeconds()).isEqualTo(1);
        assertThat(RateLimitDecision.deny(Duration.ofSeconds(60)).retryAfterSeconds()).isEqualTo(60);
        assertThat(RateLimitDecision.allow().retryAfterSeconds()).isZero();
    }
}
