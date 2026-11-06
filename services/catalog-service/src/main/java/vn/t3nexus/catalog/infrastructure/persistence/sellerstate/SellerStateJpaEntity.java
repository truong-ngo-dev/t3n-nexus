package vn.t3nexus.catalog.infrastructure.persistence.sellerstate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.t3nexus.catalog.domain.product.SellerStatus;

import java.time.Instant;
import java.util.UUID;

/** Dòng bảng {@code seller_state} (V4): bản sao trạng thái người bán, ghi qua câu lệnh có điều kiện phiên bản. */
@Entity
@Table(name = "seller_state")
@Getter
@NoArgsConstructor
public class SellerStateJpaEntity {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerStatus status;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
