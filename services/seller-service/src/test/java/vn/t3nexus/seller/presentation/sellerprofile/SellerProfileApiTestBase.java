package vn.t3nexus.seller.presentation.sellerprofile;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.t3nexus.lib.observability.http.RequestLoggingFilter;
import vn.t3nexus.seller.SellerPostgresTestBase;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lớp cơ sở test tích hợp cho SellerProfile API.
 */
public abstract class SellerProfileApiTestBase extends SellerPostgresTestBase {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MeterRegistry meterRegistry;

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
        // MockMvc mặc định mã hóa yêu cầu và phản hồi theo ISO-8859-1 (máy chủ thật thì không); ép UTF-8 để tiếng Việt đi và về đúng
        builder.defaultRequest(get("/").characterEncoding(StandardCharsets.UTF_8));
        builder.defaultResponseCharacterEncoding(StandardCharsets.UTF_8);
        this.mockMvc = builder.build();

        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");
    }

    protected RequestPostProcessor user(UUID account) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(account.toString()).claim("role", "USER"));
    }

    protected RequestPostProcessor admin(UUID account) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(account.toString()).claim("role", "ADMIN"));
    }

    protected String submitBody(String identityNumber, String shopName) {
        return """
                {
                  "sellerType": "INDIVIDUAL",
                  "legalInfo": {
                    "legalName": "Nguyễn Văn An",
                    "identityNumber": "%s",
                    "businessAddress": "12 Nguyễn Huệ, Quận 1, TP.HCM"
                  },
                  "shop": {
                    "shopName": "%s",
                    "description": "Mô tả gian hàng",
                    "contact": {
                      "personName": "Nguyễn Văn An",
                      "phone": "0912345678"
                    }
                  }
                }
                """.formatted(identityNumber, shopName);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected void submit(UUID account, String identityNumber, String shopName) throws Exception {
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody(identityNumber, shopName)))
                .andExpect(status().isCreated());
    }
}
