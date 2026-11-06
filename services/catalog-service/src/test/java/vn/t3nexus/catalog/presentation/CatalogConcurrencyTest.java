package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Kiểm thử xử lý tranh chấp đồng thời trên Postgres thật (KT-06).
 */
class CatalogConcurrencyTest extends CatalogApiTestBase {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private record Outcome(int status, String code) {
    }

    /**
     * Chạy {@code threads} luồng cùng chờ một CountDownLatch rồi cùng thực thi, thu đủ kết quả (chờ tối đa 60 giây).
     */
    private List<Outcome> race(int threads, Callable<Outcome> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    latch.await();
                    return task.call();
                }));
            }
            latch.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Outcome extractOutcome(MvcResult result) {
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
            // Không để luồng nào ném ngoại lệ ra ngoài
        }
        return new Outcome(status, code);
    }

    /**
     * 1. 20 luồng POST danh mục cấp một cùng tên "Thời trang" (luân phiên "Thời trang", "THOI TRANG")
     * -> đúng 1 luồng 201; 19 luồng còn lại đều 409 với code = CATEGORY_NAME_TAKEN;
     * bảng category có đúng 1 hàng; outbox_events có đúng 1 hàng routing_key audit.action.recorded
     * và đúng 1 hàng catalog.category.changed; không luồng nào 500.
     */
    @Test
    void twentyThreadsCreateSameRootName() throws Exception {
        String[] names = new String[]{"Thời trang", "THOI TRANG"};
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);

        List<Outcome> outcomes = race(20, () -> {
            try {
                String name = names[counter.getAndIncrement() % names.length];
                MvcResult result = mockMvc.perform(post("/api/admin/categories")
                                .with(admin(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"%s"}
                                        """.formatted(name)))
                        .andReturn();
                return extractOutcome(result);
            } catch (Exception e) {
                return new Outcome(500, e.getMessage());
            }
        });

        long created = outcomes.stream().filter(o -> o.status() == 201).count();
        long conflict = outcomes.stream().filter(o -> o.status() == 409 && "CATEGORY_NAME_TAKEN".equals(o.code())).count();
        long serverError = outcomes.stream().filter(o -> o.status() == 500).count();

        assertThat(created).isEqualTo(1);
        assertThat(conflict).isEqualTo(19);
        assertThat(serverError).isZero();

        Integer categoryCount = jdbcTemplate.queryForObject("SELECT count(*) FROM category", Integer.class);
        assertThat(categoryCount).isEqualTo(1);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE routing_key = 'audit.action.recorded'", Integer.class);
        assertThat(auditCount).isEqualTo(1);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE routing_key = 'catalog.category.changed'", Integer.class);
        assertThat(eventCount).isEqualTo(1);
    }

    /**
     * 2. 20 luồng POST danh mục con cùng tên dưới cùng một cha (cha tạo trước)
     * -> 1 luồng 201, 19 luồng 409 CATEGORY_NAME_TAKEN; bảng category có đúng 2 hàng.
     */
    @Test
    void twentyThreadsCreateSameChildName() throws Exception {
        // Tạo cha trước
        MvcResult parentResult = mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Danh mục cha"}
                                """))
                .andReturn();
        assertThat(parentResult.getResponse().getStatus()).isEqualTo(201);
        JsonNode parentJson = json(parentResult);
        String parentId = parentJson.get("data").get("id").asText();

        String[] names = new String[]{"Áo sơ mi", "AO SO MI"};
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);

        List<Outcome> outcomes = race(20, () -> {
            try {
                String name = names[counter.getAndIncrement() % names.length];
                MvcResult result = mockMvc.perform(post("/api/admin/categories")
                                .with(admin(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"%s","parentId":"%s"}
                                        """.formatted(name, parentId)))
                        .andReturn();
                return extractOutcome(result);
            } catch (Exception e) {
                return new Outcome(500, e.getMessage());
            }
        });

        long created = outcomes.stream().filter(o -> o.status() == 201).count();
        long conflict = outcomes.stream().filter(o -> o.status() == 409 && "CATEGORY_NAME_TAKEN".equals(o.code())).count();
        long serverError = outcomes.stream().filter(o -> o.status() == 500).count();

        assertThat(created).isEqualTo(1);
        assertThat(conflict).isEqualTo(19);
        assertThat(serverError).isZero();

        Integer categoryCount = jdbcTemplate.queryForObject("SELECT count(*) FROM category", Integer.class);
        assertThat(categoryCount).isEqualTo(2);
    }

    /**
     * 3. 20 luồng POST mẫu "color" -> 1 luồng 201, 19 luồng 409 ATTRIBUTE_TEMPLATE_NAME_TAKEN; bảng attribute_template 1 hàng.
     */
    @Test
    void twentyThreadsCreateSameTemplateName() throws Exception {
        List<Outcome> outcomes = race(20, () -> {
            try {
                MvcResult result = mockMvc.perform(post("/api/admin/attribute-templates")
                                .with(admin(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"color","displayName":"Màu sắc","inputType":"SINGLE_SELECT"}
                                        """))
                        .andReturn();
                return extractOutcome(result);
            } catch (Exception e) {
                return new Outcome(500, e.getMessage());
            }
        });

        long created = outcomes.stream().filter(o -> o.status() == 201).count();
        long conflict = outcomes.stream().filter(o -> o.status() == 409 && "ATTRIBUTE_TEMPLATE_NAME_TAKEN".equals(o.code())).count();
        long serverError = outcomes.stream().filter(o -> o.status() == 500).count();

        assertThat(created).isEqualTo(1);
        assertThat(conflict).isEqualTo(19);
        assertThat(serverError).isZero();

        Integer templateCount = jdbcTemplate.queryForObject("SELECT count(*) FROM attribute_template", Integer.class);
        assertThat(templateCount).isEqualTo(1);
    }

    /**
     * 4. Mẫu SINGLE_SELECT có sẵn; 20 luồng cùng thêm tùy chọn "red"
     * -> đúng 1 luồng 201; mọi luồng còn lại 409 với code thuộc {ATTRIBUTE_OPTION_VALUE_TAKEN, CONCURRENT_UPDATE};
     * bảng attribute_option có đúng 1 hàng cho mẫu đó; không luồng nào 500.
     */
    @Test
    void twentyThreadsAddSameOptionValue() throws Exception {
        MvcResult tmplResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"color","displayName":"Màu sắc","inputType":"SINGLE_SELECT"}
                                """))
                .andReturn();
        assertThat(tmplResult.getResponse().getStatus()).isEqualTo(201);
        String tmplId = json(tmplResult).get("data").get("id").asText();

        List<Outcome> outcomes = race(20, () -> {
            try {
                MvcResult result = mockMvc.perform(post("/api/admin/attribute-templates/" + tmplId + "/options")
                                .with(admin(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"value":"red","displayValue":"Đỏ"}
                                        """))
                        .andReturn();
                return extractOutcome(result);
            } catch (Exception e) {
                return new Outcome(500, e.getMessage());
            }
        });

        long created = outcomes.stream().filter(o -> o.status() == 201).count();
        long serverError = outcomes.stream().filter(o -> o.status() == 500).count();

        assertThat(created).isEqualTo(1);
        assertThat(serverError).isZero();

        Set<String> allowedConflictCodes = Set.of("ATTRIBUTE_OPTION_VALUE_TAKEN", "CONCURRENT_UPDATE");
        for (Outcome o : outcomes) {
            if (o.status() != 201) {
                assertThat(o.status()).isEqualTo(409);
                assertThat(o.code()).isIn(allowedConflictCodes);
            }
        }

        Integer optionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attribute_option WHERE template_id = ?", Integer.class, UUID.fromString(tmplId));
        assertThat(optionCount).isEqualTo(1);
    }

    /**
     * 5. 20 luồng POST thương hiệu "Apple" (luân phiên "Apple", "APPLE")
     * -> 1 luồng 201, 19 luồng 409 BRAND_NAME_TAKEN; bảng brand 1 hàng.
     */
    @Test
    void twentyThreadsCreateSameBrandName() throws Exception {
        String[] names = new String[]{"Apple", "APPLE"};
        var counter = new java.util.concurrent.atomic.AtomicInteger(0);

        List<Outcome> outcomes = race(20, () -> {
            try {
                String name = names[counter.getAndIncrement() % names.length];
                MvcResult result = mockMvc.perform(post("/api/admin/brands")
                                .with(admin(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"%s"}
                                        """.formatted(name)))
                        .andReturn();
                return extractOutcome(result);
            } catch (Exception e) {
                return new Outcome(500, e.getMessage());
            }
        });

        long created = outcomes.stream().filter(o -> o.status() == 201).count();
        long conflict = outcomes.stream().filter(o -> o.status() == 409 && "BRAND_NAME_TAKEN".equals(o.code())).count();
        long serverError = outcomes.stream().filter(o -> o.status() == 500).count();

        assertThat(created).isEqualTo(1);
        assertThat(conflict).isEqualTo(19);
        assertThat(serverError).isZero();

        Integer brandCount = jdbcTemplate.queryForObject("SELECT count(*) FROM brand", Integer.class);
        assertThat(brandCount).isEqualTo(1);
    }

    /**
     * 6. Danh mục sẵn có version=0; 20 luồng cùng PUT đổi tên, mỗi luồng một tên khác nhau ("Ten 0".."Ten 19")
     * -> mọi luồng hoặc 200 hoặc 409 với code = CONCURRENT_UPDATE (các tên khác nhau nên không có CATEGORY_NAME_TAKEN);
     * không luồng nào 500; ít nhất 1 luồng 200; version cuối trong cơ sở dữ liệu bằng số luồng 200;
     * tên cuối trong cơ sở dữ liệu là tên của một luồng 200.
     */
    @Test
    void twentyThreadsRenameSameCategory() throws Exception {
        MvcResult catResult = mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Danh mục ban đầu"}
                                """))
                .andReturn();
        assertThat(catResult.getResponse().getStatus()).isEqualTo(201);
        String catId = json(catResult).get("data").get("id").asText();

        var counter = new java.util.concurrent.atomic.AtomicInteger(0);
        List<String> assignedNames = Collections.synchronizedList(new ArrayList<>());
        List<String> successfulNames = Collections.synchronizedList(new ArrayList<>());

        List<Outcome> outcomes = race(20, () -> {
            int idx = counter.getAndIncrement();
            String name = "Ten " + idx;
            assignedNames.add(name);
            try {
                MvcResult result = mockMvc.perform(put("/api/admin/categories/" + catId)
                                .with(admin(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"%s"}
                                        """.formatted(name)))
                        .andReturn();
                Outcome outcome = extractOutcome(result);
                if (outcome.status() == 200) {
                    successfulNames.add(name);
                }
                return outcome;
            } catch (Exception e) {
                return new Outcome(500, e.getMessage());
            }
        });

        long okCount = outcomes.stream().filter(o -> o.status() == 200).count();
        long conflictCount = outcomes.stream().filter(o -> o.status() == 409 && "CONCURRENT_UPDATE".equals(o.code())).count();
        long serverError = outcomes.stream().filter(o -> o.status() == 500).count();

        assertThat(serverError).isZero();
        assertThat(okCount).isGreaterThanOrEqualTo(1);
        assertThat(okCount + conflictCount).isEqualTo(20);

        Long finalVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM category WHERE id = ?", Long.class, UUID.fromString(catId));
        String finalName = jdbcTemplate.queryForObject(
                "SELECT name FROM category WHERE id = ?", String.class, UUID.fromString(catId));

        assertThat(finalVersion).isEqualTo(okCount);
        assertThat(finalName).isIn(successfulNames);
    }

    /**
     * 7. Như ca 6 với thương hiệu.
     */
    @Test
    void twentyThreadsRenameSameBrand() throws Exception {
        MvcResult brandResult = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Thương hiệu gốc"}
                                """))
                .andReturn();
        assertThat(brandResult.getResponse().getStatus()).isEqualTo(201);
        String brandId = json(brandResult).get("data").get("id").asText();

        var counter = new java.util.concurrent.atomic.AtomicInteger(0);
        List<String> successfulNames = Collections.synchronizedList(new ArrayList<>());

        List<Outcome> outcomes = race(20, () -> {
            int idx = counter.getAndIncrement();
            String name = "Brand " + idx;
            try {
                MvcResult result = mockMvc.perform(put("/api/admin/brands/" + brandId)
                                .with(admin(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"%s"}
                                        """.formatted(name)))
                        .andReturn();
                Outcome outcome = extractOutcome(result);
                if (outcome.status() == 200) {
                    successfulNames.add(name);
                }
                return outcome;
            } catch (Exception e) {
                return new Outcome(500, e.getMessage());
            }
        });

        long okCount = outcomes.stream().filter(o -> o.status() == 200).count();
        long conflictCount = outcomes.stream().filter(o -> o.status() == 409 && "CONCURRENT_UPDATE".equals(o.code())).count();
        long serverError = outcomes.stream().filter(o -> o.status() == 500).count();

        assertThat(serverError).isZero();
        assertThat(okCount).isGreaterThanOrEqualTo(1);
        assertThat(okCount + conflictCount).isEqualTo(20);

        Long finalVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM brand WHERE id = ?", Long.class, UUID.fromString(brandId));
        String finalName = jdbcTemplate.queryForObject(
                "SELECT name FROM brand WHERE id = ?", String.class, UUID.fromString(brandId));

        assertThat(finalVersion).isEqualTo(okCount);
        assertThat(finalName).isIn(successfulNames);
    }

    /**
     * 8. Dùng bean CategoryRepository và TransactionTemplate (inject PlatformTransactionManager):
     * tạo một danh mục cấp một qua API; đọc hai bản của nó bằng categoryRepository.findById(CategoryId.of(id))
     * (hai đối tượng Category cùng version=0); đổi tên bản thứ nhất rồi lưu trong một TransactionTemplate;
     * đổi tên bản thứ hai rồi lưu trong một TransactionTemplate khác
     * -> lần lưu thứ hai ném org.springframework.orm.ObjectOptimisticLockingFailureException;
     * cơ sở dữ liệu: version=1 và tên của bản thứ nhất. Làm tương tự cho Brand.
     */
    @Test
    void twoWritersLoadingTheSameVersionOneLosesWithOptimisticLock() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // --- Kiểm tra với Category ---
        MvcResult catResult = mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Danh mục ban đầu"}
                                """))
                .andReturn();
        assertThat(catResult.getResponse().getStatus()).isEqualTo(201);
        UUID catUuid = UUID.fromString(json(catResult).get("data").get("id").asText());
        CategoryId catId = CategoryId.of(catUuid);

        Category catCopy1 = categoryRepository.findById(catId).orElseThrow();
        Category catCopy2 = categoryRepository.findById(catId).orElseThrow();
        assertThat(catCopy1.version()).isEqualTo(0L);
        assertThat(catCopy2.version()).isEqualTo(0L);

        ChangeContext ctx1 = new ChangeContext(new CatalogActor(ADMIN, "ADMIN"), UUID.randomUUID(), Instant.now());
        catCopy1.rename("Tên bản 1", ctx1);
        txTemplate.executeWithoutResult(status -> categoryRepository.save(catCopy1));

        ChangeContext ctx2 = new ChangeContext(new CatalogActor(ADMIN, "ADMIN"), UUID.randomUUID(), Instant.now());
        catCopy2.rename("Tên bản 2", ctx2);
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> categoryRepository.save(catCopy2)))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        Long catDbVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM category WHERE id = ?", Long.class, catUuid);
        String catDbName = jdbcTemplate.queryForObject(
                "SELECT name FROM category WHERE id = ?", String.class, catUuid);
        assertThat(catDbVersion).isEqualTo(1L);
        assertThat(catDbName).isEqualTo("Tên bản 1");

        // --- Kiểm tra tương tự với Brand ---
        MvcResult brandResult = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Thương hiệu ban đầu"}
                                """))
                .andReturn();
        assertThat(brandResult.getResponse().getStatus()).isEqualTo(201);
        UUID brandUuid = UUID.fromString(json(brandResult).get("data").get("id").asText());
        BrandId brandId = BrandId.of(brandUuid);

        Brand brandCopy1 = brandRepository.findById(brandId).orElseThrow();
        Brand brandCopy2 = brandRepository.findById(brandId).orElseThrow();
        assertThat(brandCopy1.version()).isEqualTo(0L);
        assertThat(brandCopy2.version()).isEqualTo(0L);

        ChangeContext bCtx1 = new ChangeContext(new CatalogActor(ADMIN, "ADMIN"), UUID.randomUUID(), Instant.now());
        brandCopy1.rename("Thương hiệu bản 1", bCtx1);
        txTemplate.executeWithoutResult(status -> brandRepository.save(brandCopy1));

        ChangeContext bCtx2 = new ChangeContext(new CatalogActor(ADMIN, "ADMIN"), UUID.randomUUID(), Instant.now());
        brandCopy2.rename("Thương hiệu bản 2", bCtx2);
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> brandRepository.save(brandCopy2)))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        Long brandDbVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM brand WHERE id = ?", Long.class, brandUuid);
        String brandDbName = jdbcTemplate.queryForObject(
                "SELECT name FROM brand WHERE id = ?", String.class, brandUuid);
        assertThat(brandDbVersion).isEqualTo(1L);
        assertThat(brandDbName).isEqualTo("Thương hiệu bản 1");
    }

    /**
     * 9. deleteRacingWithCreatingAChild: lặp 15 vòng; mỗi vòng tạo một danh mục cấp một P (qua API),
     * rồi 2 luồng cùng lúc: luồng X gọi DELETE /api/admin/categories/{P}, luồng Y gọi POST danh mục con dưới P.
     * Kết quả cho phép chỉ là một trong hai:
     * (a) X=200 và Y là 404 hoặc 409 (không phải 201, không 500);
     * (b) X=409 với code = CATEGORY_HAS_CHILDREN và Y=201.
     * Bất biến sau mỗi vòng: không có hàng nào trong bảng category mà parent_id khác NULL nhưng không có hàng cha tương ứng;
     * không có 500 ở bất kỳ vòng nào. Ghi lại mọi tổ hợp thực tế vào thông báo của assertion khi vi phạm.
     */
    @Test
    void deleteRacingWithCreatingAChild() throws Exception {
        for (int round = 1; round <= 15; round++) {
            // Tạo danh mục cấp một P
            MvcResult pResult = mockMvc.perform(post("/api/admin/categories")
                            .with(admin(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Danh mục P %d"}
                                    """.formatted(round)))
                    .andReturn();
            assertThat(pResult.getResponse().getStatus()).isEqualTo(201);
            String pId = json(pResult).get("data").get("id").asText();
            final int currentRound = round; // biến vòng lặp không dùng được trong lambda

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch latch = new CountDownLatch(1);

            Future<Outcome> futureX = pool.submit(() -> {
                latch.await();
                try {
                    MvcResult resX = mockMvc.perform(delete("/api/admin/categories/" + pId)
                                    .with(admin(ADMIN)))
                            .andReturn();
                    return extractOutcome(resX);
                } catch (Exception e) {
                    return new Outcome(500, e.getMessage());
                }
            });

            Future<Outcome> futureY = pool.submit(() -> {
                latch.await();
                try {
                    MvcResult resY = mockMvc.perform(post("/api/admin/categories")
                                    .with(admin(ADMIN))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"name":"Con cua P %d","parentId":"%s"}
                                            """.formatted(currentRound, pId)))
                            .andReturn();
                    return extractOutcome(resY);
                } catch (Exception e) {
                    return new Outcome(500, e.getMessage());
                }
            });

            latch.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            Outcome outX = futureX.get();
            Outcome outY = futureY.get();

            String comboInfo = "Round %d: X(status=%d, code=%s), Y(status=%d, code=%s)"
                    .formatted(round, outX.status(), outX.code(), outY.status(), outY.code());

            assertThat(outX.status())
                    .withFailMessage("Luồng X bị 500! " + comboInfo)
                    .isNotEqualTo(500);
            assertThat(outY.status())
                    .withFailMessage("Luồng Y bị 500! " + comboInfo)
                    .isNotEqualTo(500);

            // Tạo con dưới cha vừa bị xóa phải báo "không tìm thấy cha" (404), không được báo nhầm "còn con"
            boolean isCaseA = outX.status() == 200 && outY.status() == 404 && "CATEGORY_NOT_FOUND".equals(outY.code());
            boolean isCaseB = outX.status() == 409 && "CATEGORY_HAS_CHILDREN".equals(outX.code()) && outY.status() == 201;

            assertThat(isCaseA || isCaseB)
                    .withFailMessage("Kết quả không khớp ca (a) hoặc (b)! " + comboInfo)
                    .isTrue();

            Integer orphanCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM category c WHERE c.parent_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM category p WHERE p.id = c.parent_id)",
                    Integer.class);
            assertThat(orphanCount)
                    .withFailMessage("Phát hiện danh mục con mồ côi! " + comboInfo)
                    .isZero();
        }
    }
}
