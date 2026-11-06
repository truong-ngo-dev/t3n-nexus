package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.lib.upload.UploadSlots;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KT-06, KT-07, KT-09 (feature 08): ảnh sản phẩm với Postgres và MinIO thật (Testcontainers, không dùng MinIO dev).
 * Toàn luồng xin chỗ, tải lên bằng form ký thật, xác nhận, sắp xếp, bỏ ảnh; từng đường lỗi; đua nhau.
 */
class ProductImageApiTest extends SellerApiTestBase {

    private static final byte[] JPEG = jpeg(300);
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final GenericContainer<?> MINIO = new GenericContainer<>("quay.io/minio/minio:latest")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    private static final String ENDPOINT;

    static {
        MINIO.start();
        ENDPOINT = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    @DynamicPropertySource
    static void minio(DynamicPropertyRegistry registry) {
        registry.add("app.minio.endpoint", () -> ENDPOINT);
        registry.add("upload.purposes.product-image.public-base-url", () -> ENDPOINT);
    }

    @AfterAll
    static void stopMinio() {
        MINIO.stop();
    }

    @Autowired
    private UploadSlots uploadSlots;

    private record Slot(UUID id, String url, Map<String, String> fields) {
    }

    private record Outcome(int status, String code) {
    }

    // ───────────── Luồng chính ─────────────

    @Test
    @DisplayName("Toàn luồng: xin chỗ, tải bằng form ký, xác nhận, URL công khai đọc được, version tăng, NO_IMAGE hết")
    void fullFlow() throws Exception {
        UUID product = newProduct();
        assertThat(missing(product)).contains("NO_IMAGE");
        long before = versionOf(SELLER, product);

        Slot slot = requestSlot(product, "image/jpeg", JPEG.length);
        assertThat(upload(slot, JPEG)).isEqualTo(204);
        JsonNode image = json(confirm(SELLER, product, slot.id()).andExpect(status().isCreated()).andReturn()).path("data");

        assertThat(image.path("position").asInt()).isZero();
        assertThat(image.path("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(image.path("size").asLong()).isEqualTo(JPEG.length);
        assertThat(httpGet(image.path("url").asText())).isEqualTo(200);
        assertThat(versionOf(SELLER, product)).isEqualTo(before + 1);
        assertThat(missing(product)).doesNotContain("NO_IMAGE");
        JsonNode detail = json(getProduct(SELLER, product).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(detail.path("images")).hasSize(1);
        assertThat(detail.path("images").get(0).path("url").asText()).isEqualTo(image.path("url").asText());
        assertThat(count("upload_slot")).isZero();
        assertThat(count("product_image")).isEqualTo(1);
    }

    @Test
    @DisplayName("Sắp xếp: đổi thứ tự tăng version, cùng thứ tự không tăng, danh sách sai 409 IMAGE_ORDER_INVALID")
    void reorder() throws Exception {
        UUID product = newProduct();
        UUID a = attach(product);
        UUID b = attach(product);
        UUID c = attach(product);
        long version = versionOf(SELLER, product);

        JsonNode reordered = json(reorderRequest(SELLER, product, List.of(c, a, b))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(ids(reordered)).containsExactly(c.toString(), a.toString(), b.toString());
        assertThat(versionOf(SELLER, product)).isEqualTo(version + 1);

        reorderRequest(SELLER, product, List.of(c, a, b)).andExpect(status().isOk());
        assertThat(versionOf(SELLER, product)).isEqualTo(version + 1);

        reorderRequest(SELLER, product, List.of(c, a)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_ORDER_INVALID"));
        reorderRequest(SELLER, product, List.of(c, a, a)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_ORDER_INVALID"));
        reorderRequest(SELLER, product, List.of(c, a, UUID.randomUUID())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_ORDER_INVALID"));
        JsonNode detail = json(getProduct(SELLER, product).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(ids(detail.path("images"))).containsExactly(c.toString(), a.toString(), b.toString());
    }

    @Test
    @DisplayName("Bỏ ảnh: dồn vị trí, xóa đối tượng trên kho, version tăng; ảnh không có 404 IMAGE_NOT_FOUND")
    void remove() throws Exception {
        UUID product = newProduct();
        UUID a = attach(product);
        UUID b = attach(product);
        String urlA = imageUrl(product, a);
        long version = versionOf(SELLER, product);
        assertThat(httpGet(urlA)).isEqualTo(200);

        JsonNode left = json(mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + a).with(user(SELLER)))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(ids(left)).containsExactly(b.toString());
        assertThat(left.get(0).path("position").asInt()).isZero();
        assertThat(versionOf(SELLER, product)).isEqualTo(version + 1);
        assertThat(httpGet(urlA)).isEqualTo(404);
        mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + UUID.randomUUID()).with(user(SELLER)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
        assertThat(versionOf(SELLER, product)).isEqualTo(version + 1);
    }

    // ───────────── Đường lỗi ─────────────

    @Test
    @DisplayName("Xin chỗ: kiểu, dung lượng sai 400; thiếu trường 400; không token 401; ADMIN 403; sản phẩm người khác 404")
    void requestSlotErrors() throws Exception {
        UUID product = newProduct();

        slotRequest(SELLER, product, "{\"contentType\":\"application/pdf\",\"size\":100}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_TYPE_NOT_ALLOWED"));
        slotRequest(SELLER, product, "{\"contentType\":\"image/gif\",\"size\":100}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_TYPE_NOT_ALLOWED"));
        slotRequest(SELLER, product, "{\"contentType\":\"image/jpeg\",\"size\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_SIZE_INVALID"));
        slotRequest(SELLER, product, "{\"contentType\":\"image/jpeg\",\"size\":5242881}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_SIZE_INVALID"));
        slotRequest(SELLER, product, "{\"size\":100}").andExpect(status().isBadRequest());
        slotRequest(SELLER, product, "{\"contentType\":\"image/jpeg\"}").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/seller/products/" + product + "/images/upload-slots")
                        .contentType("application/json").content("{\"contentType\":\"image/jpeg\",\"size\":100}"))
                .andExpect(status().isUnauthorized());
        mockMvcAs(admin(ADMIN), product, "{\"contentType\":\"image/jpeg\",\"size\":100}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));
        slotRequest(OTHER_SELLER, product, "{\"contentType\":\"image/jpeg\",\"size\":100}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertThat(count("upload_slot")).isZero();
    }

    @Test
    @DisplayName("Xác nhận: chưa tải lên 422; tệp giả đuôi ảnh 422 và bị xóa; chỗ của người khác hoặc sản phẩm khác 404")
    void confirmErrors() throws Exception {
        UUID product = newProduct();
        UUID otherProduct = newProduct();

        Slot notUploaded = requestSlot(product, "image/jpeg", 100);
        confirm(SELLER, product, notUploaded.id()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_COMPLETED"));
        assertThat(count("upload_slot")).isEqualTo(1);

        Slot fake = requestSlot(product, "image/jpeg", 100);
        assertThat(upload(fake, "đây là văn bản chứ không phải ảnh".getBytes(StandardCharsets.UTF_8))).isEqualTo(204);
        confirm(SELLER, product, fake.id()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UPLOAD_CONTENT_INVALID"));
        assertThat(count("upload_slot", "id = '" + fake.id() + "'")).isZero();

        Slot good = requestSlot(product, "image/jpeg", JPEG.length);
        assertThat(upload(good, JPEG)).isEqualTo(204);
        confirm(OTHER_SELLER, product, good.id()).andExpect(status().isNotFound());
        confirm(SELLER, otherProduct, good.id()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_SLOT_NOT_FOUND"));
        confirm(SELLER, product, UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_SLOT_NOT_FOUND"));
        assertThat(count("product_image")).isZero();

        confirm(SELLER, product, good.id()).andExpect(status().isCreated());
        confirm(SELLER, product, good.id()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_SLOT_NOT_FOUND"));
        mockMvcAs(admin(ADMIN), product, "{\"slotId\":\"" + UUID.randomUUID() + "\"}", "/images").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chỗ hết hạn: xác nhận 409 UPLOAD_SLOT_EXPIRED; việc dọn xóa cả dòng và đối tượng")
    void expiredSlotAndCleaning() throws Exception {
        UUID product = newProduct();
        Slot slot = requestSlot(product, "image/jpeg", JPEG.length);
        assertThat(upload(slot, JPEG)).isEqualTo(204);
        jdbcTemplate.update("UPDATE upload_slot SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(7200)), slot.id());

        confirm(SELLER, product, slot.id()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_SLOT_EXPIRED"));
        String objectUrl = ENDPOINT + "/product-images/products/" + product + "/" + slot.id();
        assertThat(httpGet(objectUrl)).isEqualTo(200);

        assertThat(uploadSlots.cleanExpired()).isEqualTo(1);

        assertThat(count("upload_slot")).isZero();
        assertThat(httpGet(objectUrl)).isEqualTo(404);
    }

    @Test
    @DisplayName("Tối đa 9 ảnh: ảnh thứ mười bị từ chối ngay ở bước xin chỗ")
    void limitOfNine() throws Exception {
        UUID product = newProduct();
        for (int i = 0; i < 9; i++) {
            attach(product);
        }

        slotRequest(SELLER, product, "{\"contentType\":\"image/jpeg\",\"size\":100}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMAGE_LIMIT_REACHED"));
        assertThat(count("product_image")).isEqualTo(9);
    }

    // ───────────── Đua nhau (KT-07) ─────────────

    @Test
    @DisplayName("20 luồng xác nhận cùng một chỗ: đúng một ảnh, 19 lần 404, đối tượng của bên thắng còn nguyên")
    void sameSlotRace() throws Exception {
        UUID product = newProduct();
        Slot slot = requestSlot(product, "image/jpeg", JPEG.length);
        assertThat(upload(slot, JPEG)).isEqualTo(204);
        long version = versionOf(SELLER, product);

        List<Outcome> outcomes = race(20, i -> outcome(confirm(SELLER, product, slot.id())));

        assertThat(outcomes.stream().filter(o -> o.status() == 201)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.status() == 404 && "UPLOAD_SLOT_NOT_FOUND".equals(o.code()))).hasSize(19);
        assertThat(outcomes.stream().filter(o -> o.status() >= 500)).isEmpty();
        assertThat(count("product_image")).isEqualTo(1);
        assertThat(versionOf(SELLER, product)).isEqualTo(version + 1);
        String keptUrl = json(getProduct(SELLER, product).andReturn()).path("data").path("images").get(0).path("url").asText();
        assertThat(httpGet(keptUrl)).isEqualTo(200);
    }

    @Test
    @DisplayName("Đang 8 ảnh, 5 chỗ khác nhau xác nhận cùng lúc: đúng một thành công, 4 lần IMAGE_LIMIT_REACHED, không có 10 ảnh")
    void limitRace() throws Exception {
        UUID product = newProduct();
        for (int i = 0; i < 8; i++) {
            attach(product);
        }
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // Xin chỗ khi còn dưới 9 ảnh; tất cả cùng được cấp trước khi bất kỳ ai xác nhận
            Slot slot = requestSlot(product, "image/jpeg", JPEG.length);
            assertThat(upload(slot, JPEG)).isEqualTo(204);
            slots.add(slot);
        }
        long version = versionOf(SELLER, product);

        List<Outcome> outcomes = race(5, i -> outcome(confirm(SELLER, product, slots.get(i).id())));

        assertThat(outcomes.stream().filter(o -> o.status() == 201)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.status() == 409 && "IMAGE_LIMIT_REACHED".equals(o.code()))).hasSize(4);
        assertThat(count("product_image")).isEqualTo(9);
        assertThat(versionOf(SELLER, product)).isEqualTo(version + 1);
    }

    @Test
    @DisplayName("Thêm, sắp xếp, bỏ ảnh đua nhau: version tăng đúng số lệnh thành công")
    void mixedCommandsRace() throws Exception {
        UUID product = newProduct();
        List<UUID> images = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            images.add(attach(product));
        }
        long version = versionOf(SELLER, product);
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Slot slot = requestSlot(product, "image/jpeg", JPEG.length);
            assertThat(upload(slot, JPEG)).isEqualTo(204);
            slots.add(slot);
        }

        // 3 lệnh thêm, 1 lệnh bỏ, 1 lệnh sắp xếp (hai lệnh sắp xếp cùng thứ tự thì lệnh sau là thao tác rỗng, không tăng version)
        List<Outcome> outcomes = race(5, i -> {
            if (i < 3) {
                return outcome(confirm(SELLER, product, slots.get(i).id()));
            }
            if (i == 3) {
                return outcome(mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + images.get(0))
                        .with(user(SELLER))));
            }
            return outcome(reorderRequest(SELLER, product, List.of(images.get(3), images.get(2), images.get(1), images.get(0))));
        });

        assertThat(outcomes.stream().filter(o -> o.status() >= 500)).isEmpty();
        long succeeded = outcomes.stream().filter(o -> o.status() == 200 || o.status() == 201).count();
        assertThat(versionOf(SELLER, product)).as("kết quả: %s", outcomes).isEqualTo(version + succeeded);
        List<Integer> positions = new ArrayList<>();
        for (JsonNode image : json(getProduct(SELLER, product).andReturn()).path("data").path("images")) {
            positions.add(image.path("position").asInt());
        }
        assertThat(positions).isEqualTo(java.util.stream.IntStream.range(0, positions.size()).boxed().toList());
        assertThat(positions.size()).isLessThanOrEqualTo(9);
    }

    // ───────────── Trợ giúp ─────────────

    private UUID newProduct() throws Exception {
        UUID leaf = createLeaf("Ảnh " + UUID.randomUUID().toString().substring(0, 8));
        return createProduct(SELLER, leaf, "[]");
    }

    private Slot requestSlot(UUID product, String contentType, long size) throws Exception {
        JsonNode data = json(slotRequest(SELLER, product,
                "{\"contentType\":\"%s\",\"size\":%d}".formatted(contentType, size))
                .andExpect(status().isCreated()).andReturn()).path("data");
        Map<String, String> fields = new LinkedHashMap<>();
        data.path("fields").properties().forEach(e -> fields.put(e.getKey(), e.getValue().asString()));
        return new Slot(UUID.fromString(data.path("slotId").asString()), data.path("url").asString(), fields);
    }

    private ResultActions slotRequest(UUID account, UUID product, String body) throws Exception {
        return mockMvc.perform(post("/api/seller/products/" + product + "/images/upload-slots").with(user(account))
                .contentType("application/json").content(body));
    }

    private ResultActions mockMvcAs(org.springframework.test.web.servlet.request.RequestPostProcessor who, UUID product,
                                    String body) throws Exception {
        return mockMvcAs(who, product, body, "/images/upload-slots");
    }

    private ResultActions mockMvcAs(org.springframework.test.web.servlet.request.RequestPostProcessor who, UUID product,
                                    String body, String suffix) throws Exception {
        return mockMvc.perform(post("/api/seller/products/" + product + suffix).with(who)
                .contentType("application/json").content(body));
    }

    private ResultActions confirm(UUID account, UUID product, UUID slotId) throws Exception {
        return mockMvc.perform(post("/api/seller/products/" + product + "/images").with(user(account))
                .contentType("application/json").content("{\"slotId\":\"" + slotId + "\"}"));
    }

    private ResultActions reorderRequest(UUID account, UUID product, List<UUID> order) throws Exception {
        String ids = String.join(",", order.stream().map(id -> "\"" + id + "\"").toList());
        return mockMvc.perform(put("/api/seller/products/" + product + "/images/order").with(user(account))
                .contentType("application/json").content("{\"imageIds\":[" + ids + "]}"));
    }

    /** Xin chỗ, tải lên thật, xác nhận; trả mã ảnh. */
    private UUID attach(UUID product) throws Exception {
        Slot slot = requestSlot(product, "image/jpeg", JPEG.length);
        assertThat(upload(slot, JPEG)).isEqualTo(204);
        return UUID.fromString(json(confirm(SELLER, product, slot.id()).andExpect(status().isCreated()).andReturn())
                .path("data").path("id").asString());
    }

    private String imageUrl(UUID product, UUID image) throws Exception {
        for (JsonNode n : json(getProduct(SELLER, product).andReturn()).path("data").path("images")) {
            if (image.toString().equals(n.path("id").asString())) {
                return n.path("url").asString();
            }
        }
        throw new IllegalStateException("Không thấy ảnh " + image);
    }

    private List<String> missing(UUID product) throws Exception {
        List<String> codes = new ArrayList<>();
        for (JsonNode n : json(getProduct(SELLER, product).andReturn()).path("data").path("missingPublishConditions")) {
            codes.add(n.path("code").asString());
        }
        return codes;
    }

    private static List<String> ids(JsonNode images) {
        List<String> ids = new ArrayList<>();
        images.forEach(n -> ids.add(n.path("id").asString()));
        return ids;
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long count(String table, String where) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
    }

    private Outcome outcome(ResultActions actions) throws Exception {
        var response = actions.andReturn().getResponse();
        String body = response.getContentAsString();
        String code = body.isBlank() ? null : objectMapper.readTree(body).path("code").asString(null);
        return new Outcome(response.getStatus(), code);
    }

    /** Chạy {@code threads} việc cùng lúc (chốt xuất phát chung) và gom kết quả. */
    private List<Outcome> race(int threads, TaskBody body) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            Callable<Outcome> task = () -> {
                ready.countDown();
                go.await();
                return body.run(index);
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> f : futures) {
            outcomes.add(f.get());
        }
        pool.shutdown();
        return outcomes;
    }

    @FunctionalInterface
    private interface TaskBody {
        Outcome run(int index) throws Exception;
    }

    /** Gửi form multipart tới kho như trình duyệt: các trường trước, tệp (tên trường "file") cuối cùng. */
    private static int upload(Slot slot, byte[] content) throws Exception {
        String boundary = "----boundary" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : slot.fields().entrySet()) {
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(slot.url()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static int httpGet(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static byte[] jpeg(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
