package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp rollback giao dịch khi ghi kiểm toán hoặc hộp thư lỗi (KT-07).
 */
class CatalogRollbackTest extends CatalogApiTestBase {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @MockitoSpyBean
    private OutboxEventStore outboxEventStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * OutboxEventStore.store đòi giao dịch (MANDATORY): gọi nó qua proxy ngoài giao dịch, kể cả khi chỉ khai báo giả lập,
     * ném IllegalTransactionStateException trước khi tới spy. Khai báo giả lập trong một giao dịch rồi bỏ.
     */
    private void stubInTransaction(Runnable stubbing) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> stubbing.run());
    }

    @BeforeEach
    void resetSpy() {
        Mockito.reset(outboxEventStore);
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 1. categoryCreateRollsBackWhenFirstStoreFails: ép store ném IllegalStateException ở lần gọi đầu;
     * POST danh mục -> 500; bảng category 0 hàng; outbox_events 0 hàng.
     */
    @Test
    void categoryCreateRollsBackWhenFirstStoreFails() throws Exception {
        stubInTransaction(() -> doThrow(new IllegalStateException("Lỗi ghi outbox lần đầu")).when(outboxEventStore).store(any()));

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Thời trang"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(countRows("category")).isZero();
        assertThat(countRows("outbox_events")).isZero();
    }

    /**
     * 2. categoryCreateRollsBackWhenSecondStoreFails: lần gọi đầu chạy thật (doCallRealMethod()),
     * lần hai ném (doCallRealMethod().doThrow(...)); POST danh mục -> 500;
     * bảng category 0 hàng; outbox_events 0 hàng (bản ghi kiểm toán đã ghi ở lần đầu cũng bị hoàn tác).
     */
    @Test
    void categoryCreateRollsBackWhenSecondStoreFails() throws Exception {
        stubInTransaction(() -> doCallRealMethod().doThrow(new IllegalStateException("Lỗi ghi outbox lần hai")).when(outboxEventStore).store(any()));

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Thời trang"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(countRows("category")).isZero();
        assertThat(countRows("outbox_events")).isZero();
    }

    /**
     * 3. brandRenameRollsBack: tạo thương hiệu "Apple" bình thường (reset spy sau khi tạo);
     * ép store ném ở lần gọi đầu; PUT đổi thành "Apple Inc" -> 500;
     * GET lại bằng jdbcTemplate: cột name vẫn "Apple", version vẫn 0;
     * outbox_events chỉ còn các hàng của lần tạo ban đầu (không tăng).
     */
    @Test
    void brandRenameRollsBack() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = json(createResult);
        String brandId = jsonNode.get("data").get("id").asText();
        int initialOutboxCount = countRows("outbox_events");

        Mockito.reset(outboxEventStore);
        stubInTransaction(() -> doThrow(new IllegalStateException("Lỗi ghi outbox khi đổi tên")).when(outboxEventStore).store(any()));

        mockMvc.perform(put("/api/admin/brands/" + brandId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple Inc"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        String nameInDb = jdbcTemplate.queryForObject(
                "SELECT name FROM brand WHERE id = ?", String.class, UUID.fromString(brandId));
        Long versionInDb = jdbcTemplate.queryForObject(
                "SELECT version FROM brand WHERE id = ?", Long.class, UUID.fromString(brandId));

        assertThat(nameInDb).isEqualTo("Apple");
        assertThat(versionInDb).isZero();
        assertThat(countRows("outbox_events")).isEqualTo(initialOutboxCount);
    }

    /**
     * 4. templateAddOptionRollsBack: tạo mẫu SINGLE_SELECT; ép lỗi ở lần gọi đầu;
     * POST tùy chọn "red" -> 500; bảng attribute_option 0 hàng; cột version của mẫu vẫn 0.
     */
    @Test
    void templateAddOptionRollsBack() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"color","displayName":"Màu sắc","inputType":"SINGLE_SELECT"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String tmplId = json(createResult).get("data").get("id").asText();

        Mockito.reset(outboxEventStore);
        stubInTransaction(() -> doThrow(new IllegalStateException("Lỗi ghi outbox khi thêm option")).when(outboxEventStore).store(any()));

        mockMvc.perform(post("/api/admin/attribute-templates/" + tmplId + "/options")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"red","displayValue":"Đỏ"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(countRows("attribute_option")).isZero();
        Long versionInDb = jdbcTemplate.queryForObject(
                "SELECT version FROM attribute_template WHERE id = ?", Long.class, UUID.fromString(tmplId));
        assertThat(versionInDb).isZero();
    }

    /**
     * 5. categoryDeleteRollsBack: tạo danh mục; ép lỗi ở lần gọi thứ hai;
     * DELETE -> 500; bảng category vẫn còn hàng đó; outbox_events không tăng.
     */
    @Test
    void categoryDeleteRollsBack() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Gia dụng"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String catId = json(createResult).get("data").get("id").asText();
        int initialOutboxCount = countRows("outbox_events");

        Mockito.reset(outboxEventStore);
        stubInTransaction(() -> doCallRealMethod().doThrow(new IllegalStateException("Lỗi ghi outbox ở lần gọi thứ hai khi xóa")).when(outboxEventStore).store(any()));

        mockMvc.perform(delete("/api/admin/categories/" + catId)
                        .with(admin(ADMIN)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        Integer catCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM category WHERE id = ?", Integer.class, UUID.fromString(catId));
        assertThat(catCount).isEqualTo(1);
        assertThat(countRows("outbox_events")).isEqualTo(initialOutboxCount);
    }

    /**
     * 6. successfulRequestAfterFailureWorks: sau một ca lỗi, Mockito.reset(outboxEventStore)
     * (spy trở lại chạy thật) rồi cùng yêu cầu POST danh mục -> 201 (không còn trạng thái treo).
     */
    @Test
    void successfulRequestAfterFailureWorks() throws Exception {
        stubInTransaction(() -> doThrow(new IllegalStateException("Lỗi ép buộc trước")).when(outboxEventStore).store(any()));

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Thời trang"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        Mockito.reset(outboxEventStore);

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Thời trang"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Thời trang"));

        assertThat(countRows("category")).isEqualTo(1);
    }
}
