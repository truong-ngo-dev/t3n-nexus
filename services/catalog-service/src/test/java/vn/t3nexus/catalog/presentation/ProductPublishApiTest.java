package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KT-04, KT-06, KT-08 (feature 09): đăng, gỡ, đăng lại, xóa nháp, tạo nháp cần người bán đã duyệt, và sự kiện ra hộp thư
 * (ADR-0014) trên Postgres thật. Ảnh dựng bằng chèn thẳng dòng {@code product_image} (đăng không gọi kho đối tượng).
 */
class ProductPublishApiTest extends SellerApiTestBase {

    private static final String SNAPSHOT = "ProductSnapshotEvent";
    private static final String UNIT = "SellableUnitChangedEvent";

    private record Outcome(int status, String code) {
    }

    // ───────────── Tạo nháp cần người bán đã duyệt ─────────────

    @Test
    @DisplayName("Tạo nháp: chưa có bản sao hoặc bị từ chối -> 403 SELLER_NOT_APPROVED, không tạo gì; đã duyệt -> 201")
    void createNeedsApprovedSeller() throws Exception {
        UUID leaf = createLeaf("Duyệt");
        String body = "{\"title\":\"Áo thun nam\",\"categoryId\":\"%s\",\"attributes\":[]}".formatted(leaf);

        postProductAsIs(SELLER, body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_NOT_APPROVED"));
        jdbcTemplate.update("INSERT INTO seller_state (account_id, status, version, updated_at) VALUES (?, 'REJECTED', 1, now())", SELLER);
        postProductAsIs(SELLER, body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_NOT_APPROVED"));
        assertThat(count("product")).isZero();

        approve(SELLER);
        jdbcTemplate.update("UPDATE seller_state SET status = 'APPROVED' WHERE account_id = ?", SELLER);
        postProductAsIs(SELLER, body).andExpect(status().isCreated());
    }

    // ───────────── Điều kiện đăng ─────────────

    @Test
    @DisplayName("Đăng thiếu điều kiện: 409 PRODUCT_NOT_PUBLISHABLE kèm ĐỦ danh sách, không đổi gì, không ra thông điệp")
    void publishReportsAllMissingConditions() throws Exception {
        UUID leaf = createLeaf("Thiếu");
        UUID product = createProduct(SELLER, leaf, "[]");
        jdbcTemplate.update("UPDATE seller_state SET status = 'REJECTED', version = 1 WHERE account_id = ?", SELLER);
        jdbcTemplate.update("UPDATE category SET status = 'INACTIVE' WHERE id = ?", leaf);
        long version = versionOf(SELLER, product);

        JsonNode body = json(publish(SELLER, product).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_PUBLISHABLE")).andReturn());

        List<String> errors = new ArrayList<>();
        body.path("errors").forEach(e -> errors.add(e.asString()));
        assertThat(errors).containsExactlyInAnyOrder("NO_ACTIVE_VARIANT", "NO_IMAGE", "SELLER_NOT_APPROVED", "CATEGORY_NOT_USABLE");
        assertThat(versionOf(SELLER, product)).isEqualTo(version);
        assertThat(statusOf(product)).isEqualTo("DRAFT");
        assertThat(count("outbox_events", "event_type IN ('" + SNAPSHOT + "','" + UNIT + "')")).isZero();
    }

    @Test
    @DisplayName("GET sản phẩm cho thấy điều kiện còn thiếu (kể cả người bán chưa duyệt, danh mục không dùng được) trước khi bấm Đăng")
    void getShowsMissingConditions() throws Exception {
        UUID leaf = createLeaf("Xem");
        UUID product = createProduct(SELLER, leaf, "[]");
        jdbcTemplate.update("UPDATE category SET status = 'INACTIVE' WHERE id = ?", leaf);

        JsonNode detail = json(getProduct(SELLER, product).andExpect(status().isOk()).andReturn()).path("data");
        List<String> codes = new ArrayList<>();
        detail.path("missingPublishConditions").forEach(m -> codes.add(m.path("code").asString()));
        assertThat(codes).contains("NO_ACTIVE_VARIANT", "NO_IMAGE", "CATEGORY_NOT_USABLE").doesNotContain("SELLER_NOT_APPROVED");
        assertThat(detail.path("publishedAt").isNull()).isTrue();
    }

    // ───────────── Đăng, gỡ, đăng lại, xóa nháp ─────────────

    @Test
    @DisplayName("Đăng, đăng lại, gỡ: trạng thái, publishedAt giữ lần đầu, version +1 mỗi lệnh, lỗi trạng thái 409")
    void publishUnpublishRepublish() throws Exception {
        UUID product = readyProduct();
        long v0 = versionOf(SELLER, product);

        JsonNode published = json(publish(SELLER, product).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(published.path("status").asString()).isEqualTo("PUBLISHED");
        String firstPublishedAt = published.path("publishedAt").asString();
        assertThat(firstPublishedAt).isNotBlank();
        assertThat(published.path("version").asLong()).isEqualTo(v0 + 1);
        assertThat(versionOf(SELLER, product)).isEqualTo(v0 + 1);
        assertThat(published.path("missingPublishConditions")).isEmpty();

        publish(SELLER, product).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));

        JsonNode off = json(unpublish(SELLER, product).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(off.path("status").asString()).isEqualTo("UNPUBLISHED");
        assertSameInstant(off.path("publishedAt").asString(), firstPublishedAt);
        unpublish(SELLER, product).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));

        JsonNode again = json(publish(SELLER, product).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(again.path("status").asString()).isEqualTo("PUBLISHED");
        assertSameInstant(again.path("publishedAt").asString(), firstPublishedAt);
        assertThat(versionOf(SELLER, product)).isEqualTo(v0 + 3);
    }

    @Test
    @DisplayName("Đăng lại khi danh mục đã ngừng dùng -> 409 CATEGORY_NOT_USABLE, vẫn UNPUBLISHED; sản phẩm đã đăng không hết hiển thị")
    void republishNeedsUsableCategory() throws Exception {
        UUID leaf = createLeaf("Đăng lại");
        UUID product = readyProduct(leaf);
        publish(SELLER, product).andExpect(status().isOk());
        unpublish(SELLER, product).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE category SET status = 'INACTIVE' WHERE id = ?", leaf);

        JsonNode body = json(publish(SELLER, product).andExpect(status().isConflict()).andReturn());
        assertThat(body.path("errors").get(0).asString()).isEqualTo("CATEGORY_NOT_USABLE");
        assertThat(statusOf(product)).isEqualTo("UNPUBLISHED");
    }

    @Test
    @DisplayName("Xóa nháp chưa từng đăng: biến mất cùng biến thể và ảnh, không ra thông điệp; đã từng đăng thì 409 PRODUCT_NOT_DELETABLE")
    void deleteDraft() throws Exception {
        UUID product = readyProduct();
        deleteProduct(SELLER, product).andExpect(status().isOk());

        getProduct(SELLER, product).andExpect(status().isNotFound());
        assertThat(count("product")).isZero();
        assertThat(count("variant")).isZero();
        assertThat(count("product_image")).isZero();
        assertThat(count("outbox_events", "event_type IN ('" + SNAPSHOT + "','" + UNIT + "')")).isZero();

        UUID published = readyProduct();
        publish(SELLER, published).andExpect(status().isOk());
        deleteProduct(SELLER, published).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_DELETABLE"));
        unpublish(SELLER, published).andExpect(status().isOk());
        deleteProduct(SELLER, published).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_DELETABLE"));
        assertThat(count("product")).isEqualTo(1);
    }

    @Test
    @DisplayName("Bỏ ảnh cuối của sản phẩm đã đăng -> 409 PRODUCT_LAST_IMAGE; còn hai ảnh thì bỏ được")
    void lastImageOfPublishedProduct() throws Exception {
        UUID product = readyProduct();
        publish(SELLER, product).andExpect(status().isOk());
        UUID image = imageIds(product).get(0);

        mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + image).with(user(SELLER)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_LAST_IMAGE"));
        assertThat(count("product_image")).isEqualTo(1);

        insertImage(product, 1);
        mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + image).with(user(SELLER)))
                .andExpect(status().isOk());
        assertThat(count("product_image")).isEqualTo(1);
    }

    @Test
    @DisplayName("Không token 401; ADMIN 403; sản phẩm người khác hoặc không có 404 (đăng, gỡ, xóa)")
    void securityAndOwnership() throws Exception {
        UUID product = readyProduct();
        for (String path : new String[]{"/publish", "/unpublish"}) {
            mockMvc.perform(post("/api/seller/products/" + product + path)).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/seller/products/" + product + path).with(admin(ADMIN)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));
            mockMvc.perform(post("/api/seller/products/" + product + path).with(user(OTHER_SELLER)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/seller/products/" + UUID.randomUUID() + path).with(user(SELLER)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(delete("/api/seller/products/" + product)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/seller/products/" + product).with(admin(ADMIN))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/seller/products/" + product).with(user(OTHER_SELLER))).andExpect(status().isNotFound());
        assertThat(statusOf(product)).isEqualTo("DRAFT");
        assertThat(count("product")).isEqualTo(1);
    }

    // ───────────── Sự kiện ra hộp thư (ADR-0014) ─────────────

    @Test
    @DisplayName("Nháp: tạo, sửa, thêm biến thể, ảnh, xóa đều không ra thông điệp")
    void draftNeverEmits() throws Exception {
        UUID product = readyProduct();
        patchProduct(SELLER, product, "{\"title\":\"Tiêu đề mới cho nháp\"}").andExpect(status().isOk());
        insertImage(product, 1);
        deleteProduct(SELLER, product).andExpect(status().isOk());

        assertThat(count("outbox_events", "event_type IN ('" + SNAPSHOT + "','" + UNIT + "')")).isZero();
    }

    @Test
    @DisplayName("Vòng đời sự kiện (ADR-0014): đăng ra bản chụp + đơn vị; sửa khi hiển thị chỉ ra bản chụp; gỡ ra visible=false; sửa khi đã gỡ không ra gì; đăng lại ra lại")
    void eventLifecycle() throws Exception {
        UUID product = readyProduct();

        publish(SELLER, product).andExpect(status().isOk());
        List<JsonNode> snapshots = events(SNAPSHOT);
        List<JsonNode> units = events(UNIT);
        assertThat(snapshots).hasSize(1);
        assertThat(units).hasSize(1);
        JsonNode snap = snapshots.get(0).path("payload");
        assertThat(snap.path("productId").asString()).isEqualTo(product.toString());
        assertThat(snap.path("visible").asBoolean()).isTrue();
        assertThat(snap.path("version").asLong()).isEqualTo(versionOf(SELLER, product));
        assertThat(snap.path("images")).hasSize(1);
        assertThat(snap.path("variants")).hasSize(1);
        assertThat(snap.path("variants").get(0).path("price").asLong()).isEqualTo(100000L);
        assertThat(snap.path("variants").get(0).has("sku")).isFalse();
        JsonNode unit = units.get(0).path("payload");
        assertThat(unit.path("productId").asString()).isEqualTo(product.toString());
        assertThat(unit.path("active").asBoolean()).isTrue();
        assertThat(unit.path("visible").asBoolean()).isTrue();
        assertThat(unit.has("price")).isFalse();
        assertThat(unit.path("version").asLong()).isEqualTo(snap.path("version").asLong());
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT routing_key FROM outbox_events WHERE event_type = ?", String.class, SNAPSHOT))
                .containsExactly("catalog.product.snapshot");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT routing_key FROM outbox_events WHERE event_type = ?", String.class, UNIT))
                .containsExactly("catalog.sellable-unit.changed");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT aggregate_id FROM outbox_events WHERE event_type IN (?, ?)", String.class, SNAPSHOT, UNIT))
                .containsExactly(product.toString());

        // Sửa khi đang hiển thị: chỉ bản chụp mới, không đơn vị
        patchProduct(SELLER, product, "{\"title\":\"Tiêu đề đã đăng đổi\"}").andExpect(status().isOk());
        assertThat(events(SNAPSHOT)).hasSize(2);
        assertThat(events(UNIT)).hasSize(1);
        assertThat(events(SNAPSHOT).get(1).path("payload").path("title").asString()).isEqualTo("Tiêu đề đã đăng đổi");

        // Gỡ: bản chụp và đơn vị visible=false
        unpublish(SELLER, product).andExpect(status().isOk());
        assertThat(events(SNAPSHOT)).hasSize(3);
        assertThat(events(UNIT)).hasSize(2);
        assertThat(events(SNAPSHOT).get(2).path("payload").path("visible").asBoolean()).isFalse();
        assertThat(events(UNIT).get(1).path("payload").path("visible").asBoolean()).isFalse();

        // Sửa khi đã gỡ: không ra gì
        patchProduct(SELLER, product, "{\"title\":\"Sửa khi đã gỡ\"}").andExpect(status().isOk());
        assertThat(events(SNAPSHOT)).hasSize(3);
        assertThat(events(UNIT)).hasSize(2);

        // Đăng lại: ra lại toàn bộ
        publish(SELLER, product).andExpect(status().isOk());
        assertThat(events(SNAPSHOT)).hasSize(4);
        assertThat(events(UNIT)).hasSize(3);
        assertThat(events(UNIT).get(2).path("payload").path("visible").asBoolean()).isTrue();
        List<Long> versions = new ArrayList<>();
        events(SNAPSHOT).forEach(e -> versions.add(e.path("payload").path("version").asLong()));
        assertThat(versions).isSorted();
        assertThat(versions).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Sản phẩm đang hiển thị: ngừng biến thể ra cả bản chụp lẫn đơn vị (active=false); đổi giá chỉ ra bản chụp")
    void variantChangesWhileVisible() throws Exception {
        UUID product = readyProduct();
        publish(SELLER, product).andExpect(status().isOk());
        UUID variant = jdbcTemplate.queryForObject("SELECT id FROM variant WHERE product_id = ?", UUID.class, product);

        patchVariant(SELLER, product, variant, "{\"price\":150000}").andExpect(status().isOk());
        assertThat(events(SNAPSHOT)).hasSize(2);
        assertThat(events(UNIT)).hasSize(1);
        assertThat(events(SNAPSHOT).get(1).path("payload").path("variants").get(0).path("price").asLong()).isEqualTo(150000L);

        mockMvc.perform(post("/api/seller/products/" + product + "/variants/" + variant + "/deactivate").with(user(SELLER)))
                .andExpect(status().isOk());
        assertThat(events(SNAPSHOT)).hasSize(3);
        assertThat(events(UNIT)).hasSize(2);
        JsonNode unit = events(UNIT).get(1).path("payload");
        assertThat(unit.path("variantId").asString()).isEqualTo(variant.toString());
        assertThat(unit.path("active").asBoolean()).isFalse();
        assertThat(unit.path("visible").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Lệnh thất bại không ra thông điệp: đăng thiếu điều kiện, đăng hai lần")
    void failedCommandsEmitNothing() throws Exception {
        UUID leaf = createLeaf("Hỏng");
        UUID product = createProduct(SELLER, leaf, "[]");
        publish(SELLER, product).andExpect(status().isConflict());
        assertThat(count("outbox_events", "event_type IN ('" + SNAPSHOT + "','" + UNIT + "')")).isZero();

        UUID ready = readyProduct();
        publish(SELLER, ready).andExpect(status().isOk());
        long afterFirst = count("outbox_events", "event_type IN ('" + SNAPSHOT + "','" + UNIT + "')");
        publish(SELLER, ready).andExpect(status().isConflict());
        assertThat(count("outbox_events", "event_type IN ('" + SNAPSHOT + "','" + UNIT + "')")).isEqualTo(afterFirst);
    }

    // ───────────── Đua nhau ─────────────

    @Test
    @DisplayName("20 luồng đăng cùng lúc: đúng 1 lần 200, 19 lần 409 CATALOG_INVALID_STATE; version +1; đúng 1 bản chụp")
    void publishRace() throws Exception {
        UUID product = readyProduct();
        long version = versionOf(SELLER, product);

        List<Outcome> outcomes = race(20, i -> outcome(publish(SELLER, product)));

        assertThat(outcomes.stream().filter(o -> o.status() == 200)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.status() == 409 && "CATALOG_INVALID_STATE".equals(o.code()))).hasSize(19);
        assertThat(outcomes.stream().filter(o -> o.status() >= 500)).isEmpty();
        assertThat(versionOf(SELLER, product)).isEqualTo(version + 1);
        assertThat(statusOf(product)).isEqualTo("PUBLISHED");
        assertThat(count("outbox_events", "event_type = '" + SNAPSHOT + "'")).isEqualTo(1);
    }

    @Test
    @DisplayName("Đăng đua với thêm biến thể và bỏ ảnh: kết quả luôn nhất quán, version đúng số lệnh thành công")
    void publishRacesWithEdits() throws Exception {
        UUID product = readyProduct();
        insertImage(product, 1);
        long version = versionOf(SELLER, product);
        List<UUID> images = imageIds(product);

        List<Outcome> outcomes = race(3, i -> switch (i) {
            case 0 -> outcome(publish(SELLER, product));
            case 1 -> outcome(mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + images.get(0)).with(user(SELLER))));
            default -> outcome(mockMvc.perform(patch("/api/seller/products/" + product).with(user(SELLER))
                    .contentType("application/json").content("{\"title\":\"Tiêu đề đang sửa\"}")));
        });

        assertThat(outcomes.stream().filter(o -> o.status() >= 500)).isEmpty();
        long succeeded = outcomes.stream().filter(o -> o.status() == 200).count();
        assertThat(versionOf(SELLER, product)).isEqualTo(version + succeeded);
        assertThat(count("product_image", "product_id = '" + product + "'")).isGreaterThanOrEqualTo(1);
    }

    // ───────────── Trợ giúp ─────────────

    /** Sản phẩm nháp đủ điều kiện đăng: một biến thể mặc định hoạt động và một ảnh. */
    private UUID readyProduct() throws Exception {
        return readyProduct(createLeaf("Sẵn " + UUID.randomUUID().toString().substring(0, 8)));
    }

    private UUID readyProduct(UUID leaf) throws Exception {
        UUID product = createProduct(SELLER, leaf, "[]");
        createVariant(SELLER, product, "[]", 100000, "SKU-" + UUID.randomUUID().toString().substring(0, 8));
        insertImage(product, 0);
        return product;
    }

    private void insertImage(UUID product, int position) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO product_image (id, product_id, object_key, content_type, size_bytes, position) "
                + "VALUES (?, ?, ?, 'image/jpeg', 1000, ?)", id, product, "products/" + product + "/" + id, position);
    }

    private List<UUID> imageIds(UUID product) {
        return jdbcTemplate.queryForList("SELECT id FROM product_image WHERE product_id = ? ORDER BY position", UUID.class, product);
    }

    private ResultActions publish(UUID account, UUID product) throws Exception {
        return mockMvc.perform(post("/api/seller/products/" + product + "/publish").with(user(account)));
    }

    private ResultActions unpublish(UUID account, UUID product) throws Exception {
        return mockMvc.perform(post("/api/seller/products/" + product + "/unpublish").with(user(account)));
    }

    private ResultActions deleteProduct(UUID account, UUID product) throws Exception {
        return mockMvc.perform(delete("/api/seller/products/" + product).with(user(account)));
    }

    /** Lệnh vừa ghi trả thời điểm nano giây; đọc lại từ Postgres thì làm tròn micro giây: lệch tối đa 1 micro giây là cùng thời điểm. */
    private static void assertSameInstant(String actual, String expected) {
        assertThat(java.time.Duration.between(java.time.Instant.parse(expected), java.time.Instant.parse(actual)).abs())
                .isLessThanOrEqualTo(java.time.Duration.ofNanos(1000));
    }

    private String statusOf(UUID product) {
        return jdbcTemplate.queryForObject("SELECT status FROM product WHERE id = ?", String.class, product);
    }

    /** Các bản ghi hộp thư (phong bì đã phân tích) của một loại sự kiện, theo thứ tự ghi. */
    private List<JsonNode> events(String eventType) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        for (String payload : jdbcTemplate.queryForList(
                "SELECT payload FROM outbox_events WHERE event_type = ? ORDER BY id", String.class, eventType)) {
            result.add(objectMapper.readTree(payload));
        }
        return result;
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long count(String table, String where) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
    }

    private Outcome outcome(ResultActions actions) throws Exception {
        var response = actions.andReturn().getResponse();
        String text = response.getContentAsString();
        String code = text.isBlank() ? null : objectMapper.readTree(text).path("code").asString(null);
        return new Outcome(response.getStatus(), code);
    }

    @FunctionalInterface
    private interface TaskBody {
        Outcome run(int index) throws Exception;
    }

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
}
