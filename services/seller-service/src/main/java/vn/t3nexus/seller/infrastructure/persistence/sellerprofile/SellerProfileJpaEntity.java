package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Thực thể JPA đại diện cho hồ sơ người bán trong cơ sở dữ liệu.
 */
@Entity
@Table(name = "seller_profiles")
@Getter
@Setter
@NoArgsConstructor
public class SellerProfileJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_account_id", nullable = false, updatable = false)
    private UUID ownerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerProfileStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_type", nullable = false, length = 20)
    private SellerType sellerType;

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "representative_name", length = 100)
    private String representativeName;

    @Column(name = "identity_number", nullable = false, length = 12)
    private String identityNumber;

    @Column(name = "tax_code", length = 13)
    private String taxCode;

    @Column(name = "business_address", nullable = false, length = 300)
    private String businessAddress;

    @Column(name = "identity_key", nullable = false, length = 13)
    private String identityKey;

    @Column(name = "shop_name", nullable = false, length = 60)
    private String shopName;

    @Column(name = "shop_name_key", nullable = false, length = 60)
    private String shopNameKey;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 10)
    private String contactPhone;

    @Column(name = "contact_address", length = 300)
    private String contactAddress;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
