package vn.t3nexus.storefrontwebbff.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Máy chủ HTTP giả (JDK {@link HttpServer}, không thêm phụ thuộc) đóng vai dịch vụ sau cổng: Xác thực, dịch vụ nghiệp vụ sau BFF.
 * Ghi lại mọi yêu cầu nhận được để test kiểm đường dẫn, tiêu đề và thân mà cổng đã chuyển xuống; trả phản hồi đã đăng ký theo
 * (phương thức, đường dẫn), không có thì 404. Cổng ngẫu nhiên trên 127.0.0.1, mỗi thể hiện một cổng.
 */
public final class FakeHttpServer implements AutoCloseable {

    /** Một yêu cầu đã nhận; tên tiêu đề chuyển chữ thường để so sánh. */
    public record Received(String method, String path, String query, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            List<String> values = headers.get(name.toLowerCase());
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    private record Canned(int status, String contentType, String body, Map<String, String> headers) {
    }

    private final HttpServer server;
    private volatile long delayMillis;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Map<String, Canned> responses = Collections.synchronizedMap(new LinkedHashMap<>());

    public FakeHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    /** Trả {@code status} và {@code body} cho yêu cầu {@code method path} (so khớp chính xác đường dẫn, không tính chuỗi truy vấn). */
    public FakeHttpServer respond(String method, String path, int status, String contentType, String body) {
        return respond(method, path, status, contentType, body, Map.of());
    }

    public FakeHttpServer respond(String method, String path, int status, String contentType, String body, Map<String, String> headers) {
        responses.put(method.toUpperCase() + " " + path, new Canned(status, contentType, body, headers));
        return this;
    }

    /** Xóa yêu cầu đã ghi và phản hồi đã đăng ký (gọi ở {@code @BeforeEach}). */
    public void reset() {
        received.clear();
        delayMillis = 0;
        responses.clear();
    }

    /** Mọi phản hồi chậm thêm chừng này (mô phỏng Xác thực chậm); {@link #reset()} đặt lại 0. */
    public void delayResponses(long millis) {
        this.delayMillis = millis;
    }

    public List<Received> requests() {
        return new ArrayList<>(received);
    }

    public Received last() {
        List<Received> all = requests();
        if (all.isEmpty()) throw new AssertionError("máy chủ giả chưa nhận yêu cầu nào");
        return all.get(all.size() - 1);
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v));
        String path = exchange.getRequestURI().getPath();
        received.add(new Received(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), headers, body));

        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Canned canned = responses.get(exchange.getRequestMethod().toUpperCase() + " " + path);
        int status = canned == null ? 404 : canned.status();
        byte[] bytes = (canned == null ? "" : canned.body()).getBytes(StandardCharsets.UTF_8);
        if (canned != null) {
            exchange.getResponseHeaders().add("Content-Type", canned.contentType());
            canned.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
