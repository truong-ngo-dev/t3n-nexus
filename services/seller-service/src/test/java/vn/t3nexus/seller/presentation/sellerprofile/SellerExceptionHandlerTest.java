package vn.t3nexus.seller.presentation.sellerprofile;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử đơn vị cho SellerExceptionHandler bằng MockMvc độc lập.
 */
@ExtendWith(OutputCaptureExtension.class)
class SellerExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/boom/{kind}")
        public void boom(@PathVariable("kind") String kind) {
            switch (kind) {
                case "domain-exists" -> throw new DomainException(SellerErrorCode.SELLER_PROFILE_EXISTS);
                case "domain-field" -> throw new SellerFieldException(SellerErrorCode.INVALID_IDENTITY_NUMBER, "identityNumber");
                case "domain-field-nested" -> throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "contact.personName");
                case "dup-owner" -> throw new DataIntegrityViolationException("could not execute statement; ERROR: duplicate key value violates unique constraint\n  Detail: Key (identity_key)=(079123456789) already exists.",
                        new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_seller_profiles_owner\"\n  Detail: Key (identity_key)=(079123456789) already exists."));
                case "dup-shop" -> throw new DataIntegrityViolationException("could not execute statement; ERROR: duplicate key value violates unique constraint\n  Detail: Key (identity_key)=(079123456789) already exists.",
                        new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_seller_profiles_shop_name_key\"\n  Detail: Key (identity_key)=(079123456789) already exists."));
                case "dup-identity" -> throw new DataIntegrityViolationException("could not execute statement; ERROR: duplicate key value violates unique constraint\n  Detail: Key (identity_key)=(079123456789) already exists.",
                        new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_seller_profiles_identity_key_effective\"\n  Detail: Key (identity_key)=(079123456789) already exists."));
                case "dup-unknown" -> throw new DataIntegrityViolationException("could not execute statement; ERROR: duplicate key value violates unique constraint\n  Detail: Key (identity_key)=(079123456789) already exists.",
                        new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_something_else\"\n  Detail: Key (identity_key)=(079123456789) already exists."));
                case "dup-noname" -> throw new DataIntegrityViolationException("could not execute statement; ERROR: duplicate key value violates unique constraint\n  Detail: Key (identity_key)=(079123456789) already exists.",
                        new RuntimeException("ERROR: duplicate key value violates unique constraint\n  Detail: Key (identity_key)=(079123456789) already exists."));
                case "lock" -> throw new ObjectOptimisticLockingFailureException(Object.class, "x");
                default -> throw new IllegalArgumentException("Unknown kind: " + kind);
            }
        }
    }

    private SimpleMeterRegistry registry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new SellerExceptionHandler(registry), new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("domain-exists và domain-field")
    void testDomainExceptions() throws Exception {
        mockMvc.perform(get("/boom/domain-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_EXISTS"))
                .andExpect(jsonPath("$.errors").isEmpty());

        mockMvc.perform(get("/boom/domain-field"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_IDENTITY_NUMBER"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("identityNumber")));
    }

    @Test
    @DisplayName("Lỗi trường: tên trường nằm ở errors[0] để giao diện gắn vào đúng ô, không phải cắt từ message")
    void fieldErrorCarriesFieldName() throws Exception {
        mockMvc.perform(get("/boom/domain-field"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]").value("identityNumber"));

        mockMvc.perform(get("/boom/domain-field-nested"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.errors[0]").value("contact.personName"));
    }

    @Test
    @DisplayName("dup-owner, dup-shop, dup-identity, lock trả 409 và mã tương ứng")
    void testKnownDataIntegrityAndLockExceptions() throws Exception {
        mockMvc.perform(get("/boom/dup-owner"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_EXISTS"));

        mockMvc.perform(get("/boom/dup-shop"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SHOP_NAME_TAKEN"));

        mockMvc.perform(get("/boom/dup-identity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_REGISTERED"));

        mockMvc.perform(get("/boom/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    @DisplayName("dup-unknown và dup-noname trả 500 INTERNAL_ERROR và thông báo chung")
    void testUnknownDataIntegrityExceptions() throws Exception {
        mockMvc.perform(get("/boom/dup-unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

        mockMvc.perform(get("/boom/dup-noname"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    @DisplayName("Nội dung phản hồi của mọi ca dup-* không chứa 079123456789 và không chứa Detail")
    void testNoLeakedDataInResponses() throws Exception {
        for (String kind : new String[]{"dup-owner", "dup-shop", "dup-identity", "dup-unknown", "dup-noname"}) {
            MvcResult result = mockMvc.perform(get("/boom/" + kind)).andReturn();
            String content = result.getResponse().getContentAsString();
            assertThat(content)
                    .as("Check kind: " + kind)
                    .doesNotContain("079123456789")
                    .doesNotContain("Detail");
        }
    }

    @Test
    @DisplayName("Bộ đếm xung đột tăng đúng theo mã")
    void testConflictCounters() throws Exception {
        mockMvc.perform(get("/boom/domain-exists"));
        mockMvc.perform(get("/boom/domain-field"));
        mockMvc.perform(get("/boom/dup-owner"));
        mockMvc.perform(get("/boom/dup-shop"));
        mockMvc.perform(get("/boom/dup-identity"));
        mockMvc.perform(get("/boom/lock"));

        assertThat(registry.counter("seller.profile.conflict", "code", "SHOP_NAME_TAKEN").count()).isEqualTo(1.0);
        assertThat(registry.counter("seller.profile.conflict", "code", "CONCURRENT_UPDATE").count()).isEqualTo(1.0);
        assertThat(registry.counter("seller.profile.conflict", "code", "SELLER_PROFILE_EXISTS").count()).isEqualTo(2.0);
        assertThat(registry.find("seller.profile.conflict").tags("code", "INVALID_IDENTITY_NUMBER").counter()).isNull();
    }

    @Test
    @DisplayName("Log không chứa 079123456789, ca dup-shop chứa SELLER_CONFLICT và SHOP_NAME_TAKEN, dup-unknown chứa SELLER_UNMAPPED_CONSTRAINT và uq_something_else")
    void testLoggingOutput(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/boom/dup-owner"));
        mockMvc.perform(get("/boom/dup-shop"));
        mockMvc.perform(get("/boom/dup-identity"));
        mockMvc.perform(get("/boom/dup-unknown"));
        mockMvc.perform(get("/boom/dup-noname"));

        String logs = output.getAll();
        assertThat(logs).doesNotContain("079123456789");
        assertThat(logs).contains("SELLER_CONFLICT", "SHOP_NAME_TAKEN");
        assertThat(logs).contains("SELLER_UNMAPPED_CONSTRAINT", "uq_something_else");
    }
}
