package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ánh xạ tên ràng buộc sang mã lỗi (data.md mục 8). Mỗi dòng của bảng có một ca; ràng buộc lạ thì 500. Ngoại lệ dựng
 * giống thật: {@code ConstraintViolationException} của Hibernate bọc trong {@code DataIntegrityViolationException}.
 */
class PersistenceConflictExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/boom/constraint/{name}")
        public void constraint(@PathVariable("name") String name) {
            throw new DataIntegrityViolationException("could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException("violation", new SQLException("x"), name));
        }

        /** Hibernate không tách được tên: chỉ còn thông báo của trình điều khiển Postgres. */
        @GetMapping("/boom/message/{name}")
        public void message(@PathVariable("name") String name) {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("ERROR: duplicate key value violates unique constraint \"" + name + "\""));
        }

        /** Cùng khóa ngoài cha nhưng câu lệnh khác nhau: chèn con dưới cha vừa xóa, hoặc xóa cha còn con. */
        @GetMapping("/boom/fk-statement/{kind}")
        public void fkStatement(@PathVariable("kind") String kind) {
            String sql = "insert".equals(kind) ? "insert into category (id,parent_id) values (?,?)" : "delete from category where id=?";
            throw new DataIntegrityViolationException("could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException("violation", new SQLException("x"), sql,
                            "fk_category_parent"));
        }

        @GetMapping("/boom/no-name")
        public void noName() {
            throw new DataIntegrityViolationException("could not execute statement", new SQLException("oops"));
        }

        @GetMapping("/boom/optimistic")
        public void optimistic() {
            throw new ObjectOptimisticLockingFailureException("Category", "id");
        }

        @GetMapping("/boom/optimistic-generic")
        public void optimisticGeneric() {
            throw new OptimisticLockingFailureException("stale");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new PersistenceConflictExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private void expectConflict(String path, String code) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void variantUniqueConstraintsMapToTheirCodes() throws Exception {
        expectConflict("/boom/constraint/uq_variant_product_combination", "VARIANT_COMBINATION_TAKEN");
        expectConflict("/boom/constraint/uq_variant_seller_sku", "VARIANT_SKU_TAKEN");
        expectConflict("/boom/message/uq_variant_seller_sku", "VARIANT_SKU_TAKEN");
    }

    @Test
    void categoryNameConstraintsMapToCategoryNameTaken() throws Exception {
        expectConflict("/boom/constraint/uq_category_parent_name_key", "CATEGORY_NAME_TAKEN");
        expectConflict("/boom/constraint/uq_category_root_name_key", "CATEGORY_NAME_TAKEN");
    }

    @Test
    void templateAndOptionConstraintsMapToTheirCodes() throws Exception {
        expectConflict("/boom/constraint/uq_attribute_template_name", "ATTRIBUTE_TEMPLATE_NAME_TAKEN");
        expectConflict("/boom/constraint/uq_attribute_option_template_value", "ATTRIBUTE_OPTION_VALUE_TAKEN");
    }

    @Test
    void brandNameConstraintMapsToBrandNameTaken() throws Exception {
        expectConflict("/boom/constraint/uq_brand_name_key", "BRAND_NAME_TAKEN");
    }

    @Test
    void parentForeignKeyMapsToHasChildren() throws Exception {
        expectConflict("/boom/constraint/fk_category_parent", "CATEGORY_HAS_CHILDREN");
    }

    /** Chèn con dưới cha vừa bị xóa: cha không còn (404), không được báo nhầm là "còn con". */
    @Test
    void parentForeignKeyOnInsertMeansParentIsGone() throws Exception {
        mockMvc.perform(get("/boom/fk-statement/insert"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        expectConflict("/boom/fk-statement/delete", "CATEGORY_HAS_CHILDREN");
    }

    @Test
    void constraintNameIsFoundInDriverMessageWhenHibernateGivesNone() throws Exception {
        expectConflict("/boom/message/uq_brand_name_key", "BRAND_NAME_TAKEN");
        expectConflict("/boom/message/fk_category_parent", "CATEGORY_HAS_CHILDREN");
    }

    @Test
    void optimisticLockMapsToConcurrentUpdate() throws Exception {
        expectConflict("/boom/optimistic", "CONCURRENT_UPDATE");
        expectConflict("/boom/optimistic-generic", "CONCURRENT_UPDATE");
    }

    /** Ràng buộc không có trong bảng thì để lộ, không đoán: 500, không lọt thành 409. */
    @Test
    void unknownConstraintIsASystemError() throws Exception {
        mockMvc.perform(get("/boom/constraint/ck_category_level"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        mockMvc.perform(get("/boom/constraint/fk_caa_template"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void violationWithoutAnyConstraintNameIsASystemError() throws Exception {
        mockMvc.perform(get("/boom/no-name"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void conflictMessageComesFromTheErrorCodeNotTheDatabase() throws Exception {
        mockMvc.perform(get("/boom/constraint/uq_brand_name_key"))
                .andExpect(jsonPath("$.message").value("Tên thương hiệu đã được sử dụng"));
    }
}
