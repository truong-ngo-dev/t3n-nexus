package vn.t3nexus.seller.presentation.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử bổ sung cho SellerExceptionHandler với các mã lỗi của feature 04 (STALE_VERSION, REJECTION_REASON_REQUIRED).
 */
@ExtendWith(OutputCaptureExtension.class)
class SellerExceptionHandlerFeature04Test {

    @RestController
    static class ThrowingController {
        @GetMapping("/boom-f04/{kind}")
        public void boom(@PathVariable("kind") String kind) {
            switch (kind) {
                case "stale-version" -> throw new DomainException(SellerErrorCode.STALE_VERSION);
                case "rejection-reason-required" -> throw new DomainException(SellerErrorCode.REJECTION_REASON_REQUIRED);
                case "invalid-state" -> throw new DomainException(SellerErrorCode.INVALID_STATE);
                case "reason-field" -> throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "reason");
                default -> throw new IllegalArgumentException("Unknown kind: " + kind);
            }
        }
    }

    private SimpleMeterRegistry registry;
    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new SellerExceptionHandler(registry), new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("staleVersionReturns409AndLogsConflictAndIncrementsCounter: STALE_VERSION trả 409, log SELLER_CONFLICT và tăng bộ đếm")
    void staleVersionReturns409AndLogsConflictAndIncrementsCounter(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/boom-f04/stale-version"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        // Kiểm tra log chứa SELLER_CONFLICT STALE_VERSION
        assertThat(output.getAll()).contains("SELLER_CONFLICT", "STALE_VERSION");

        // Kiểm tra bộ đếm seller.profile.conflict{code=STALE_VERSION}
        assertThat(registry.counter("seller.profile.conflict", "code", "STALE_VERSION").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("rejectionReasonRequiredReturns400: REJECTION_REASON_REQUIRED trả 400")
    void rejectionReasonRequiredReturns400() throws Exception {
        mockMvc.perform(get("/boom-f04/rejection-reason-required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));
    }

    @Test
    @DisplayName("invalidStateReturns409: INVALID_STATE trả 409")
    void invalidStateReturns409() throws Exception {
        mockMvc.perform(get("/boom-f04/invalid-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("reasonFieldExceptionReturns400WithFieldName: lỗi trường reason trả 400 và thông báo nêu tên trường")
    void reasonFieldExceptionReturns400WithFieldName() throws Exception {
        mockMvc.perform(get("/boom-f04/reason-field"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reason")));
    }
}
