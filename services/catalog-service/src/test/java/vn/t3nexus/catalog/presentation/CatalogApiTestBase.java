package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.catalog.CatalogPostgresTestBase;
import vn.t3nexus.catalog.CatalogServiceApplication;
import vn.t3nexus.lib.observability.http.RequestLoggingFilter;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Lớp cơ sở test API của catalog-service: cả ứng dụng với Postgres thật, MockMvc có bộ lọc bảo mật (nên 401 và 403
 * được kiểm đúng như chạy thật) và JWT giả với {@code sub} là mã tài khoản, claim {@code role}. Mỗi ca bắt đầu với bảng
 * rỗng.
 */
// Listener Kafka tắt: các test API không cần Kafka; lớp test nhận sự kiện bật lại với EmbeddedKafka
@SpringBootTest(classes = CatalogServiceApplication.class, properties = "spring.kafka.listener.auto-startup=false")
public abstract class CatalogApiTestBase extends CatalogPostgresTestBase {

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected ObjectMapper objectMapper;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpBase() {
        RequestLoggingFilter loggingFilter = null;
        for (FilterRegistrationBean<?> reg : context.getBeansOfType(FilterRegistrationBean.class).values()) {
            if (reg.getFilter() instanceof RequestLoggingFilter rlf) {
                loggingFilter = rlf;
                break;
            }
        }
        var builder = MockMvcBuilders.webAppContextSetup(context);
        if (loggingFilter != null) {
            builder.addFilters(loggingFilter);
        }
        builder.apply(SecurityMockMvcConfigurers.springSecurity());
        // MockMvc mặc định mã hóa theo ISO-8859-1 (máy chủ thật thì không); ép UTF-8 để tiếng Việt đi và về đúng
        builder.defaultRequest(get("/").characterEncoding(StandardCharsets.UTF_8));
        builder.defaultResponseCharacterEncoding(StandardCharsets.UTF_8);
        this.mockMvc = builder.build();

        // Container cách ly của riêng test, nên xóa sạch được; CASCADE vì có khóa ngoài giữa các bảng
        jdbcTemplate.execute("TRUNCATE TABLE seller_state, upload_slot, product_image, variant_combination_item, variant, product_attribute_option, product_attribute, product, "
                + "category_attribute_assignment, category, attribute_option, "
                + "attribute_template, brand, outbox_events CASCADE");
    }

    protected RequestPostProcessor user(UUID account) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(account.toString()).claim("role", "USER"));
    }

    protected RequestPostProcessor admin(UUID account) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(account.toString()).claim("role", "ADMIN"));
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
