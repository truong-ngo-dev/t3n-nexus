package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ứng dụng khởi động được với Postgres thật: Flyway áp {@code V1}, {@code ddl-auto=validate} thấy mọi thực thể khớp
 * bảng, bộ lọc bảo mật và các bean (kiểm toán, hộp thư) dựng được. Đây là điều kiện đầu cho mọi test API còn lại.
 */
class CatalogApplicationBootTest extends CatalogApiTestBase {

    @Test
    void flywayAppliedTheBaselineAndEveryTableExists() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("brand", "attribute_template", "attribute_option", "category",
                "category_attribute_assignment", "outbox_events");
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    /** Không có đường nào truy cập được khi chưa đăng nhập; đường ngoài /api/** bị chặn kể cả khi đã đăng nhập (denyAll). */
    @Test
    void everyOtherPathIsDeniedEvenForAdmin() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api-docs").with(admin(UUID.randomUUID()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/anything").with(user(UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    @Test
    void loggedInUserReadsAnEmptyTree() throws Exception {
        mockMvc.perform(get("/api/categories").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
