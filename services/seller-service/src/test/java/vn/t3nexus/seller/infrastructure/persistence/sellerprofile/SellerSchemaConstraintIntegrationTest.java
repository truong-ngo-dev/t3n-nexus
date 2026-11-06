package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử tích hợp các ràng buộc lược đồ cơ sở dữ liệu cho seller_profiles và outbox_events (KT-06).
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerSchemaConstraintIntegrationTest extends SellerPostgresTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");
    }

    private void insertRaw(
            String status,
            String sellerType,
            String representativeName,
            String taxCode,
            String rejectionReason,
            boolean reviewed,
            String identityKey,
            String shopNameKey,
            UUID owner
    ) {
        UUID id = UUID.randomUUID();
        String legalName = "Nguyễn Văn An";
        String identityNumber = "079123456789";
        String businessAddress = "12 Nguyễn Huệ, Quận 1, TP.HCM";
        String shopName = shopNameKey;
        String description = "Mô tả gian hàng";
        String contactName = "Nguyễn Văn An";
        String contactPhone = "0912345678";
        String contactAddress = null;
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);
        Timestamp reviewedAt = reviewed ? nowTs : null;
        UUID reviewedBy = reviewed ? UUID.randomUUID() : null;
        long version = 0L;

        jdbcTemplate.update(
                "INSERT INTO seller_profiles (" +
                        "id, owner_account_id, status, seller_type, legal_name, representative_name, " +
                        "identity_number, tax_code, business_address, identity_key, shop_name, shop_name_key, " +
                        "description, contact_name, contact_phone, contact_address, rejection_reason, " +
                        "submitted_at, reviewed_at, reviewed_by, version, created_at, updated_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, owner, status, sellerType, legalName, representativeName,
                identityNumber, taxCode, businessAddress, identityKey, shopName, shopNameKey,
                description, contactName, contactPhone, contactAddress, rejectionReason,
                nowTs, reviewedAt, reviewedBy, version, nowTs, nowTs
        );
    }

    @Test
    @DisplayName("Lịch sử migration Flyway áp dụng đủ 3 phiên bản và có đủ bảng")
    void migrationsApplied() {
        // Kiểm tra flyway_schema_history có đúng 3 phiên bản 1, 2 và 3 thành công (V3: chỉ mục hàng chờ, feature 04)
        List<String> installedVersions = jdbcTemplate.query(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                (rs, rowNum) -> rs.getString("version")
        );
        assertThat(installedVersions).containsExactly("1", "2", "3");

        // Kiểm tra tồn tại 2 bảng seller_profiles và outbox_events
        List<String> tables = jdbcTemplate.query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('seller_profiles', 'outbox_events')",
                (rs, rowNum) -> rs.getString("table_name")
        );
        assertThat(tables).containsExactlyInAnyOrder("seller_profiles", "outbox_events");
    }

    @Test
    @DisplayName("Bảng outbox_events có đủ các cột chuẩn và chặn trùng event_id")
    void outboxEventsHasTemplateColumns() {
        List<String> columns = jdbcTemplate.query(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'outbox_events'",
                (rs, rowNum) -> rs.getString("column_name")
        );
        assertThat(columns).contains(
                "id", "event_id", "aggregate_type", "aggregate_id", "event_type",
                "routing_key", "payload", "occurred_on", "created_at", "trace_id", "span_id"
        );

        // Chèn 2 hàng cùng event_id phải bị chặn bởi uq_outbox_events_event_id
        String eventId = "evt-" + UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                "INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, event_type, routing_key, payload, occurred_on, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                eventId, "SellerProfile", UUID.randomUUID().toString(), "SellerSubmitted", "seller.submitted", "{}", now, now
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, event_type, routing_key, payload, occurred_on, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                eventId, "SellerProfile", UUID.randomUUID().toString(), "SellerSubmitted", "seller.submitted", "{}", now, now
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_outbox_events_event_id"));
    }

    @Test
    @DisplayName("Ràng buộc trạng thái từ chối giá trị ngoài danh sách cho phép")
    void statusOutsideSetRejected() {
        assertThatThrownBy(() -> insertRaw(
                "CANCELLED", "INDIVIDUAL", null, null, null, true,
                "079123456789", "Shop Cancelled", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("ck_seller_profiles_status"));
    }

    @Test
    @DisplayName("Ràng buộc loại người bán từ chối giá trị ngoài danh sách cho phép")
    void sellerTypeOutsideSetRejected() {
        assertThatThrownBy(() -> insertRaw(
                "PENDING_REVIEW", "COMPANY", null, "0312345678", null, false,
                "079123456789", "Shop Company", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("ck_seller_profiles_type"));
    }

    @Test
    @DisplayName("Doanh nghiệp bắt buộc phải có tên người đại diện")
    void businessWithoutRepresentativeRejected() {
        // Không có đại diện -> bị chặn bởi ck_seller_profiles_representative
        assertThatThrownBy(() -> insertRaw(
                "PENDING_REVIEW", "BUSINESS", null, "0123456789", null, false,
                "0123456789", "Shop Biz No Rep", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("ck_seller_profiles_representative"));

        // Có đại diện -> chèn thành công
        insertRaw("PENDING_REVIEW", "BUSINESS", "Lê Quang Cường", "0123456789", null, false,
                "0123456789", "Shop Biz With Rep", UUID.randomUUID());
    }

    @Test
    @DisplayName("Hộ kinh doanh bắt buộc phải có mã số thuế")
    void householdWithoutTaxCodeRejected() {
        // Hộ kinh doanh không có mã số thuế -> bị chặn bởi ck_seller_profiles_tax_code
        assertThatThrownBy(() -> insertRaw(
                "PENDING_REVIEW", "HOUSEHOLD", null, null, null, false,
                "079123456789", "Shop Household No Tax", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("ck_seller_profiles_tax_code"));

        // Cá nhân không có mã số thuế -> chèn được
        insertRaw("PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                "079123456789", "Shop Individual No Tax", UUID.randomUUID());
    }

    @Test
    @DisplayName("Hồ sơ bị từ chối bắt buộc phải có lý do từ chối")
    void rejectedWithoutReasonRejected() {
        // REJECTED nhưng rejectionReason NULL -> bị chặn bởi ck_seller_profiles_rejection_reason
        assertThatThrownBy(() -> insertRaw(
                "REJECTED", "INDIVIDUAL", null, null, null, true,
                "079123456789", "Shop Rejected No Reason", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("ck_seller_profiles_rejection_reason"));

        // Có lý do -> chèn được
        insertRaw("REJECTED", "INDIVIDUAL", null, null, "Giấy tờ không hợp lệ", true,
                "079123456789", "Shop Rejected With Reason", UUID.randomUUID());
    }

    @Test
    @DisplayName("Trạng thái đã duyệt bắt buộc phải có thông tin người duyệt và thời điểm duyệt")
    void reviewedStatusWithoutReviewerRejected() {
        // APPROVED nhưng reviewed = false -> bị chặn bởi ck_seller_profiles_review
        assertThatThrownBy(() -> insertRaw(
                "APPROVED", "INDIVIDUAL", null, null, null, false,
                "079123456789", "Shop Approved No Review", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("ck_seller_profiles_review"));

        // APPROVED có reviewed = true -> chèn được
        insertRaw("APPROVED", "INDIVIDUAL", null, null, null, true,
                "079123456789", "Shop Approved With Review", UUID.randomUUID());

        // PENDING_REVIEW với reviewed = false -> chèn được
        insertRaw("PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                "079123456788", "Shop Pending Review", UUID.randomUUID());
    }

    @Test
    @DisplayName("Ràng buộc duy nhất chủ sở hữu uq_seller_profiles_owner chặn trùng chủ")
    void ownerUniqueConstraintNamed() {
        UUID owner = UUID.randomUUID();
        insertRaw("PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                "079123456781", "Shop Name 1", owner);

        assertThatThrownBy(() -> insertRaw(
                "PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                "079123456782", "Shop Name 2", owner
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_owner"));
    }

    @Test
    @DisplayName("Ràng buộc duy nhất tên gian hàng chuẩn hóa uq_seller_profiles_shop_name_key")
    void shopNameKeyUniqueConstraintNamed() {
        String shopNameKey = "cua hang hoa";
        insertRaw("PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                "079123456781", shopNameKey, UUID.randomUUID());

        assertThatThrownBy(() -> insertRaw(
                "PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                "079123456782", shopNameKey, UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_shop_name_key"));
    }

    @Test
    @DisplayName("Chỉ mục duy nhất một phần identity_key_effective cho phép tái dùng khóa khi hồ sơ bị từ chối")
    void identityKeyPartialUniqueIndex() {
        String key = "079123456789";

        // (a) Hai hàng khác chủ, cùng identity_key, cả hai PENDING_REVIEW -> hàng sau bị uq_seller_profiles_identity_key_effective
        insertRaw("PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                key, "Shop A", UUID.randomUUID());

        assertThatThrownBy(() -> insertRaw(
                "PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                key, "Shop B", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_identity_key_effective"));

        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");

        // (b) Hàng đầu REJECTED (đủ lý do, đã reviewed) và hàng sau PENDING_REVIEW cùng khóa -> CHÈN ĐƯỢC
        insertRaw("REJECTED", "INDIVIDUAL", null, null, "Không hợp lệ", true,
                key, "Shop Rejected", UUID.randomUUID());
        insertRaw("PENDING_REVIEW", "INDIVIDUAL", null, null, null, false,
                key, "Shop Pending", UUID.randomUUID());

        // (c) Sau (b), chèn hàng thứ ba APPROVED (đã reviewed), khác chủ, cùng khóa -> bị chỉ mục
        assertThatThrownBy(() -> insertRaw(
                "APPROVED", "INDIVIDUAL", null, null, null, true,
                key, "Shop Approved", UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_identity_key_effective"));

        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");

        // (d) Hai hàng REJECTED cùng khóa -> chèn được cả hai
        insertRaw("REJECTED", "INDIVIDUAL", null, null, "Lý do 1", true,
                key, "Shop Rejected 1", UUID.randomUUID());
        insertRaw("REJECTED", "INDIVIDUAL", null, null, "Lý do 2", true,
                key, "Shop Rejected 2", UUID.randomUUID());
    }

    @Test
    @DisplayName("Định nghĩa chỉ mục một phần uq_seller_profiles_identity_key_effective chứa UNIQUE, WHERE và REJECTED")
    void partialIndexDefinitionHasWhereClause() {
        String indexDef = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'seller_profiles' AND indexname = 'uq_seller_profiles_identity_key_effective'",
                String.class
        );
        assertThat(indexDef).isNotNull();
        assertThat(indexDef)
                .contains("UNIQUE")
                .contains("WHERE")
                .contains("REJECTED");
    }
}
