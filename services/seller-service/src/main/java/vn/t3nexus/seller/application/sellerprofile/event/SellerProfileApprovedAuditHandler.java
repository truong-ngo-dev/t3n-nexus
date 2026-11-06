package vn.t3nexus.seller.application.sellerprofile.event;

import org.springframework.stereotype.Component;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileApproved;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Xử lý sự kiện duyệt hồ sơ người bán bằng cách ghi bản ghi kiểm toán.
 */
@Component
public class SellerProfileApprovedAuditHandler implements EventHandler<SellerProfileApproved> {

    private final AuditRecorder auditRecorder;

    public SellerProfileApprovedAuditHandler(AuditRecorder auditRecorder) {
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder");
    }

    @Override
    public void handle(SellerProfileApproved event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("viewedVersion", event.viewedVersion());
        details.put("sellerAccountId", event.accountId().toString());

        auditRecorder.record(
                SellerAuditActions.PROFILE_APPROVED,
                new AuditActor(event.reviewedBy(), SellerAuditActions.ADMIN_ROLE),
                new AuditTarget(SellerAuditActions.TARGET_TYPE, event.profileId().getValue().toString()),
                details
        );
    }

    @Override
    public Class<SellerProfileApproved> getEventType() {
        return SellerProfileApproved.class;
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
