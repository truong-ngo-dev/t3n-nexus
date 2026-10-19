package vn.t3nexus.lib.observability.http;

import io.micrometer.tracing.handler.TracingObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bản WebFlux của {@link RequestLoggingFilter} cho cổng và BFF: MỘT dòng hoàn tất cho mỗi yêu cầu (INFO, logger {@code http.request}), cùng tên trường
 * ({@code event.dataset}, {@code http.request.method}, {@code url.path}, {@code http.response.status_code}, {@code event.duration},
 * {@code client.ip}), ghi lúc kết thúc nên phủ cả yêu cầu bị từ chối ở bước đầu (host lạ, 401, 429, 404). Không ghi nội dung yêu cầu khi lỗi (cổng và BFF
 * không đọc thân yêu cầu; việc đệm thân trong WebFlux chưa làm).
 * <p>
 * {@code traceId}/{@code spanId} lấy trực tiếp từ ngữ cảnh quan sát của yêu cầu (đối tượng do {@code HttpWebHandlerAdapter} đặt vào thuộc tính của yêu cầu trước khi chạy chuỗi lọc)
 * chứ không từ MDC của luồng hiện tại, vì ở WebFlux việc ghi log có thể xảy ra trên luồng khác. {@code client.ip} theo {@link ClientIpResolver}.
 * Không bao giờ làm hỏng yêu cầu: lỗi khi ghi log được nuốt.
 */
public class ReactiveRequestLoggingFilter implements WebFilter, Ordered {

    /** Sớm hơn Spring Security và bộ lọc giới hạn tần suất để phủ cả yêu cầu bị từ chối ở đó. */
    public static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.LOGGER_NAME);

    private final HttpLoggingProperties properties;
    private final ClientIpResolver clientIps;

    public ReactiveRequestLoggingFilter(HttpLoggingProperties properties, ClientIpResolver clientIps) {
        this.properties = properties;
        this.clientIps = clientIps;
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        for (String prefix : properties.ignorePaths()) {
            if (path.startsWith(prefix)) return chain.filter(exchange);
        }
        long start = System.nanoTime();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        return chain.filter(exchange)
                .doOnError(failure::set)
                .doFinally(signal -> logCompleted(exchange, failure.get(), System.nanoTime() - start));
    }

    private void logCompleted(ServerWebExchange exchange, Throwable failure, long durationNanos) {
        try {
            int status = statusOf(exchange, failure);
            var request = exchange.getRequest();

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("event.dataset", "http.request");
            fields.put("http.request.method", request.getMethod().name());
            fields.put("url.path", request.getPath().value());
            fields.put("http.response.status_code", String.valueOf(status));
            fields.put("event.duration", String.valueOf(durationNanos));
            long requestBytes = request.getHeaders().getContentLength();
            if (requestBytes >= 0) fields.put("http.request.body.bytes", String.valueOf(requestBytes));
            long responseBytes = exchange.getResponse().getHeaders().getContentLength();
            if (responseBytes >= 0) fields.put("http.response.body.bytes", String.valueOf(responseBytes));
            InetSocketAddress remote = request.getRemoteAddress();
            String remoteAddress = remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
            fields.put("client.ip", clientIps.resolve(remoteAddress, request.getHeaders().getFirst(ClientIpResolver.HEADER)));
            putTrace(exchange, fields);

            emit(fields, request.getMethod().name() + " " + request.getPath().value() + " " + status);
        } catch (RuntimeException ignored) {
            // log không được làm hỏng yêu cầu
        }
    }

    private static int statusOf(ServerWebExchange exchange, Throwable failure) {
        HttpStatusCode committed = exchange.getResponse().getStatusCode();
        if (failure == null) return committed != null ? committed.value() : 200;
        if (failure instanceof ResponseStatusException rse) return rse.getStatusCode().value();
        return committed != null && committed.value() >= 400 ? committed.value() : 500;
    }

    /** Lấy mã truy vết từ ngữ cảnh quan sát của yêu cầu; không có (tắt truy vết) thì bỏ qua. */
    private static void putTrace(ServerWebExchange exchange, Map<String, String> fields) {
        ServerRequestObservationContext.findCurrent(exchange.getAttributes()).ifPresent(context -> {
            TracingObservationHandler.TracingContext tracing = context.get(TracingObservationHandler.TracingContext.class);
            if (tracing == null || tracing.getSpan() == null) return;
            fields.put("traceId", tracing.getSpan().context().traceId());
            fields.put("spanId", tracing.getSpan().context().spanId());
        });
    }

    /** Đặt các trường vào MDC ngay trước khi ghi rồi gỡ đúng những khóa mình đã đặt (không đụng khóa có sẵn). */
    private static void emit(Map<String, String> fields, String message) {
        List<String> added = new ArrayList<>();
        try {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (MDC.get(field.getKey()) == null) {
                    MDC.put(field.getKey(), field.getValue());
                    added.add(field.getKey());
                }
            }
            log.info(message);
        } finally {
            for (String key : added) MDC.remove(key);
        }
    }
}
