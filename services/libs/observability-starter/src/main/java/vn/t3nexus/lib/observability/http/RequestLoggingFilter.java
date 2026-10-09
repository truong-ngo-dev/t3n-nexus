package vn.t3nexus.lib.observability.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import vn.t3nexus.lib.observability.logging.LogContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ghi MỘT dòng hoàn tất cho mỗi yêu cầu HTTP (INFO, logger {@code http.request}), như nhật ký truy cập: method, khuôn đường dẫn, mã trạng thái,
 * thời lượng, kích thước, địa chỉ mạng, mã lỗi. Ghi lúc kết thúc, trong {@code finally}, nên phủ cả yêu cầu bị từ chối trước khi tới controller.
 * Nội dung yêu cầu (đã làm sạch bởi {@link BodySanitizer}) chỉ được ghi khi lỗi 5xx hoặc 4xx do đầu vào. Dòng "vào" ở DEBUG.
 * Không bao giờ làm hỏng yêu cầu: lỗi khi ghi log được nuốt. Xem {@code docs/global/4.convention/logging.md} mục 6, 7.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String LOGGER_NAME = "http.request";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    /** Thuộc tính yêu cầu do bộ xử lý lỗi chung đặt (cùng tên với `GlobalExceptionHandler.ERROR_CODE_KEY`). */
    public static final String ERROR_CODE_ATTRIBUTE = "error.code";

    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

    private final HttpLoggingProperties properties;
    private final BodySanitizer sanitizer;

    public RequestLoggingFilter(HttpLoggingProperties properties, BodySanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : properties.ignorePaths()) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        ContentCachingRequestWrapper cached = wantsBodyCapture(request)
                ? new ContentCachingRequestWrapper(request, properties.maxBodyBytes()) : null;
        HttpServletRequest effective = cached != null ? cached : request;

        // Khôi phục MDC về trạng thái đầu khi xong: khóa use case đặt trong yêu cầu (labels.*, user.id) không rò sang yêu cầu sau trên cùng luồng.
        // Dòng hoàn tất được ghi trong finally bên trong, tức trước khi phạm vi đóng, nên vẫn mang các khóa đó.
        try (LogContext.Scope ignored = LogContext.scope()) {
            logReceived(request);

            Throwable failure = null;
            try {
                chain.doFilter(effective, response);
            } catch (ServletException | IOException | RuntimeException | Error e) {
                failure = e;
                throw e;
            } finally {
                logCompleted(request, response, cached, failure, System.nanoTime() - start);
            }
        }
    }

    private boolean wantsBodyCapture(HttpServletRequest request) {
        if (!METHODS_WITH_BODY.contains(request.getMethod())) return false;
        String contentType = request.getContentType();
        if (contentType == null) return false;
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("json") || lower.startsWith("text/");
    }

    private void logReceived(HttpServletRequest request) {
        try {
            if (!log.isDebugEnabled()) return;
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("event.dataset", "http.request");
            fields.put("http.request.method", request.getMethod());
            fields.put("url.path", request.getRequestURI());
            long length = request.getContentLengthLong();
            if (length >= 0) fields.put("http.request.body.bytes", String.valueOf(length));
            fields.put("client.ip", request.getRemoteAddr());
            emit(fields, false, "received " + request.getMethod() + " " + request.getRequestURI());
        } catch (RuntimeException ignored) {
            // log không được làm hỏng yêu cầu
        }
    }

    private void logCompleted(HttpServletRequest request, HttpServletResponse response, ContentCachingRequestWrapper cached,
                              Throwable failure, long durationNanos) {
        try {
            int status = failure != null && response.getStatus() < 400 ? 500 : response.getStatus();

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("event.dataset", "http.request");
            fields.put("http.request.method", request.getMethod());
            String route = routeOf(request);
            if (route != null) fields.put("http.route", route);
            fields.put("url.path", request.getRequestURI());
            fields.put("http.response.status_code", String.valueOf(status));
            fields.put("event.duration", String.valueOf(durationNanos));
            long requestBytes = request.getContentLengthLong();
            if (requestBytes >= 0) fields.put("http.request.body.bytes", String.valueOf(requestBytes));
            String responseBytes = response.getHeader("Content-Length");
            if (responseBytes != null) fields.put("http.response.body.bytes", responseBytes);
            fields.put("client.ip", request.getRemoteAddr());
            Object errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE);
            if (errorCode != null) fields.put("error.code", errorCode.toString());

            if (cached != null && wantsBody(status)) {
                String body = sanitizeBody(cached, request);
                if (body != null) fields.put("http.request.body.content", body);
            }

            String message = request.getMethod() + " " + (route != null ? route : request.getRequestURI()) + " " + status;
            emit(fields, true, message);
        } catch (RuntimeException ignored) {
            // log không được làm hỏng yêu cầu
        }
    }

    private boolean wantsBody(int status) {
        if (status >= 500) return true;
        return status >= 400 && !properties.noBodyStatuses().contains(status);
    }

    private String sanitizeBody(ContentCachingRequestWrapper cached, HttpServletRequest request) {
        try {
            return sanitizer.sanitize(cached.getContentAsByteArray(), request.getContentType(), allowedFields(request));
        } catch (RuntimeException e) {
            return BodySanitizer.UNREADABLE;
        }
    }

    private Set<String> allowedFields(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod method) {
            LogRequestFields annotation = method.getMethodAnnotation(LogRequestFields.class);
            if (annotation != null) return new HashSet<>(List.of(annotation.value()));
        }
        return null;
    }

    private String routeOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : null;
    }

    /** Đặt các trường vào MDC ngay trước khi ghi rồi gỡ đúng những khóa mình đã đặt (không đụng khóa có sẵn). */
    private void emit(Map<String, String> fields, boolean info, String message) {
        List<String> added = new ArrayList<>();
        try {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (MDC.get(field.getKey()) == null) {
                    MDC.put(field.getKey(), field.getValue());
                    added.add(field.getKey());
                }
            }
            if (info) {
                log.info(message);
            } else {
                log.debug(message);
            }
        } finally {
            for (String key : added) MDC.remove(key);
        }
    }
}
