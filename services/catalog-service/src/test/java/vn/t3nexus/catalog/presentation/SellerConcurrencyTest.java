package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * KT-06: các quy tắc cắt ngang sản phẩm và biến thể khi nhiều yêu cầu đến cùng lúc, trên Postgres thật (ADR-0012).
 * Tối đa 50 biến thể (đếm, không phải tính duy nhất), phiên bản sản phẩm tăng đúng một mỗi lệnh, tổ hợp duy nhất, SKU
 * duy nhất giữa hai sản phẩm của một người bán, sửa tiêu đề và đổi trục đua với thêm biến thể. Không luồng nào được 5xx.
 */
class SellerConcurrencyTest extends SellerApiTestBase {

    private static final int COLORS = 10;
    private static final int SIZES = 7;

    private UUID leaf;
    private UUID color;
    private UUID size;
    private final List<UUID> colorOptions = new ArrayList<>();
    private final List<UUID> sizeOptions = new ArrayList<>();

    private record Outcome(int status, String code) {
    }

    @BeforeEach
    void structure() throws Exception {
        colorOptions.clear();
        sizeOptions.clear();
        leaf = createLeaf("Áo");
        color = createTemplate("color", "SINGLE_SELECT");
        size = createTemplate("size_label", "SINGLE_SELECT");
        for (int i = 0; i < COLORS; i++) colorOptions.add(addOption(color, "c" + i));
        for (int i = 0; i < SIZES; i++) sizeOptions.add(addOption(size, "s" + i));
        assignAttributes(leaf, "[" + item(color, false, 1) + "," + item(size, false, 2) + "]");
    }

    // ---- dựng dữ liệu ----

    private UUID productWithAxes() throws Exception {
        return productWithAxes(SELLER);
    }

    private UUID productWithAxes(UUID owner) throws Exception {
        return createProduct(owner, leaf, "[" + axis(color, colorOptions.toArray(UUID[]::new)) + ","
                + axis(size, sizeOptions.toArray(UUID[]::new)) + "]");
    }

    /** Tổ hợp thứ {@code index} trong 70 tổ hợp (10 màu x 7 cỡ). */
    private String combination(int index) {
        return "[" + pair(color, colorOptions.get(index / SIZES)) + "," + pair(size, sizeOptions.get(index % SIZES)) + "]";
    }

    private String variantBody(int index, long price, String sku) {
        return "{\"combination\":" + combination(index) + ",\"price\":" + price
                + (sku == null ? "" : ",\"sku\":\"" + sku + "\"") + "}";
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    // ---- chạy đồng thời ----

    /** Chạy mọi tác vụ cùng lúc sau một rào chắn, thu đủ kết quả theo thứ tự (chờ tối đa 90 giây). */
    private List<Outcome> raceAll(List<Callable<Outcome>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        try {
            for (Callable<Outcome> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(90, TimeUnit.SECONDS)).isTrue();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Outcome outcomeOf(MvcResult result) {
        int status = result.getResponse().getStatus();
        String code = null;
        try {
            String content = result.getResponse().getContentAsString();
            if (content != null && !content.isBlank()) {
                JsonNode tree = objectMapper.readTree(content);
                if (tree.has("code") && !tree.get("code").isNull()) {
                    code = tree.get("code").asText();
                }
            }
        } catch (Exception ignored) {
            // luồng nào cũng phải trả kết quả, không ném ra ngoài
        }
        return new Outcome(status, code);
    }

    private Callable<Outcome> addVariant(UUID account, UUID productId, String body) {
        return () -> outcomeOf(mockMvc.perform(post("/api/seller/products/" + productId + "/variants").with(user(account))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn());
    }

    private static long countStatus(List<Outcome> outcomes, int status) {
        return outcomes.stream().filter(o -> o.status() == status).count();
    }

    private static long countCode(List<Outcome> outcomes, String code) {
        return outcomes.stream().filter(o -> code.equals(o.code())).count();
    }

    private static void assertNoServerErrors(List<Outcome> outcomes) {
        assertThat(outcomes).noneMatch(o -> o.status() >= 500);
    }

    // ---- tối đa 50 biến thể ----

    @Test
    @DisplayName("Sản phẩm đang có 49 biến thể, 20 luồng thêm 20 tổ hợp khác nhau: đúng một thành công, 19 VARIANT_LIMIT_REACHED, không có 51")
    void limitIsExactUnderRace() throws Exception {
        UUID product = productWithAxes();
        for (int i = 0; i < 49; i++) {
            createVariant(SELLER, product, combination(i), 100_000L, null);
        }
        assertThat(versionOf(SELLER, product)).isEqualTo(49);

        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 49; i < 69; i++) {
            tasks.add(addVariant(SELLER, product, variantBody(i, 100_000L, null)));
        }
        List<Outcome> outcomes = raceAll(tasks);

        assertNoServerErrors(outcomes);
        assertThat(countStatus(outcomes, 201)).isEqualTo(1);
        assertThat(countCode(outcomes, "VARIANT_LIMIT_REACHED")).isEqualTo(19);
        assertThat(count("SELECT count(*) FROM variant WHERE product_id = ?", product)).isEqualTo(50);
        assertThat(versionOf(SELLER, product)).isEqualTo(50);
    }

    @Test
    @DisplayName("Sản phẩm trống, 20 luồng thêm 20 tổ hợp khác nhau: cả 20 thành công, version sản phẩm tăng đúng 20 (không nhảy, không trùng)")
    void versionIsSequentialUnderRace() throws Exception {
        UUID product = productWithAxes();
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(addVariant(SELLER, product, variantBody(i, 100_000L + i, null)));
        }
        List<Outcome> outcomes = raceAll(tasks);

        assertNoServerErrors(outcomes);
        assertThat(countStatus(outcomes, 201)).isEqualTo(20);
        assertThat(count("SELECT count(*) FROM variant WHERE product_id = ?", product)).isEqualTo(20);
        assertThat(versionOf(SELLER, product)).isEqualTo(20);
    }

    @Test
    @DisplayName("20 luồng cùng thêm một tổ hợp: đúng một thành công, 19 VARIANT_COMBINATION_TAKEN")
    void sameCombinationOnlyOnce() throws Exception {
        UUID product = productWithAxes();
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(addVariant(SELLER, product, variantBody(3, 100_000L, null)));
        }
        List<Outcome> outcomes = raceAll(tasks);

        assertNoServerErrors(outcomes);
        assertThat(countStatus(outcomes, 201)).isEqualTo(1);
        assertThat(countCode(outcomes, "VARIANT_COMBINATION_TAKEN")).isEqualTo(19);
        assertThat(count("SELECT count(*) FROM variant WHERE product_id = ?", product)).isEqualTo(1);
        assertThat(versionOf(SELLER, product)).isEqualTo(1);
    }

    @Test
    @DisplayName("Cùng SKU trong hai sản phẩm của một người bán, 20 luồng: đúng một thành công, còn lại VARIANT_SKU_TAKEN")
    void sameSkuAcrossTwoProductsOnlyOnce() throws Exception {
        UUID first = productWithAxes();
        UUID second = productWithAxes();
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(addVariant(SELLER, i % 2 == 0 ? first : second, variantBody(i / 2, 100_000L, "SKU-RACE")));
        }
        List<Outcome> outcomes = raceAll(tasks);

        assertNoServerErrors(outcomes);
        assertThat(countStatus(outcomes, 201)).isEqualTo(1);
        assertThat(countCode(outcomes, "VARIANT_SKU_TAKEN")).isEqualTo(19);
        assertThat(count("SELECT count(*) FROM variant WHERE sku = 'SKU-RACE'")).isEqualTo(1);
    }

    @Test
    @DisplayName("Cùng SKU ở hai người bán khác nhau: cả hai thành công (SKU chỉ duy nhất trong người bán)")
    void sameSkuDifferentSellersBothSucceed() throws Exception {
        UUID mine = productWithAxes();
        UUID theirs = productWithAxes(OTHER_SELLER);
        List<Outcome> outcomes = raceAll(List.of(
                addVariant(SELLER, mine, variantBody(0, 100_000L, "SKU-SHARED")),
                addVariant(OTHER_SELLER, theirs, variantBody(0, 100_000L, "SKU-SHARED"))));
        assertThat(outcomes).allMatch(o -> o.status() == 201);
    }

    // ---- đổi giá đua nhau ----

    @Test
    @DisplayName("20 luồng đổi giá cùng một biến thể: cả 20 thành công, version sản phẩm tăng đúng 20")
    void priceChangesAreSerialized() throws Exception {
        UUID product = productWithAxes();
        UUID variant = createVariant(SELLER, product, combination(0), 100_000L, null);
        long before = versionOf(SELLER, product);

        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            long price = 100_000L + i;
            tasks.add(() -> outcomeOf(mockMvc.perform(patch("/api/seller/products/" + product + "/variants/" + variant)
                    .with(user(SELLER)).contentType(MediaType.APPLICATION_JSON).content("{\"price\":" + price + "}")).andReturn()));
        }
        List<Outcome> outcomes = raceAll(tasks);

        assertNoServerErrors(outcomes);
        assertThat(countStatus(outcomes, 200)).isEqualTo(20);
        assertThat(versionOf(SELLER, product)).isEqualTo(before + 20);
    }

    // ---- sửa thông tin đua với thêm biến thể ----

    @Test
    @DisplayName("Sửa tiêu đề đua với thêm biến thể: không mất biến thể nào, version sản phẩm đúng bằng số lệnh thành công, bên sửa chỉ gặp 409 CONCURRENT_UPDATE")
    void titleEditRacesWithAddingVariants() throws Exception {
        UUID product = productWithAxes();
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(addVariant(SELLER, product, variantBody(i, 100_000L, null)));
        }
        for (int i = 0; i < 20; i++) {
            String title = "Tiêu đề lần " + i;
            tasks.add(() -> outcomeOf(mockMvc.perform(patch("/api/seller/products/" + product).with(user(SELLER))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}")).andReturn()));
        }
        List<Outcome> outcomes = raceAll(tasks);

        assertNoServerErrors(outcomes);
        List<Outcome> adds = outcomes.subList(0, 20);
        List<Outcome> edits = outcomes.subList(20, 40);
        assertThat(adds).allMatch(o -> o.status() == 201);
        assertThat(edits).allMatch(o -> o.status() == 200
                || (o.status() == 409 && "CONCURRENT_UPDATE".equals(o.code())));
        assertThat(count("SELECT count(*) FROM variant WHERE product_id = ?", product)).isEqualTo(20);
        long successes = 20 + countStatus(edits, 200);
        assertThat(versionOf(SELLER, product)).isEqualTo(successes);
    }

    // ---- đổi trục đua với thêm biến thể ----

    @Test
    @DisplayName("Bỏ một giá trị trục đua với thêm biến thể dùng giá trị đó: luôn nhất quán, không có biến thể ngoài tập trục")
    void axisChangeRacesWithAddingVariant() throws Exception {
        UUID dropped = colorOptions.get(1);
        List<UUID> products = new ArrayList<>();
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int p = 0; p < 12; p++) {
            UUID product = createProduct(SELLER, leaf, "[" + axis(color, colorOptions.get(0), dropped) + ","
                    + axis(size, sizeOptions.get(0)) + "]");
            products.add(product);
            tasks.add(() -> outcomeOf(mockMvc.perform(patch("/api/seller/products/" + product).with(user(SELLER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"attributes\":[" + axis(color, colorOptions.get(0)) + "," + axis(size, sizeOptions.get(0)) + "]}"))
                    .andReturn()));
            tasks.add(addVariant(SELLER, product, "{\"combination\":[" + pair(color, dropped) + ","
                    + pair(size, sizeOptions.get(0)) + "],\"price\":100000}"));
        }
        List<Outcome> outcomes = raceAll(tasks);

        assertNoServerErrors(outcomes);
        for (int p = 0; p < products.size(); p++) {
            Outcome edit = outcomes.get(2 * p);
            Outcome add = outcomes.get(2 * p + 1);
            boolean variantUsesDropped = count("SELECT count(*) FROM variant_combination_item i JOIN variant v ON v.id = i.variant_id "
                    + "WHERE v.product_id = ? AND i.option_id = ?", products.get(p), dropped) > 0;
            boolean axisKeepsDropped = count("SELECT count(*) FROM product_attribute_option WHERE product_id = ? AND option_id = ?",
                    products.get(p), dropped) > 0;
            // hoặc sửa trục thắng (biến thể bị từ chối), hoặc thêm biến thể thắng (sửa trục bị từ chối); không bao giờ cả hai
            if (edit.status() == 200) {
                assertThat(add.status()).isEqualTo(400);
                assertThat(add.code()).isEqualTo("VARIANT_COMBINATION_INVALID");
                assertThat(variantUsesDropped).isFalse();
                assertThat(axisKeepsDropped).isFalse();
            } else {
                assertThat(edit.status()).isEqualTo(409);
                assertThat(edit.code()).isEqualTo("PRODUCT_AXIS_VALUE_IN_USE");
                assertThat(add.status()).isEqualTo(201);
                assertThat(variantUsesDropped).isTrue();
                assertThat(axisKeepsDropped).isTrue();
            }
            assertThat(variantUsesDropped && !axisKeepsDropped).isFalse();
        }
    }
}
