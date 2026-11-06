package vn.t3nexus.seller.presentation.sellerprofile;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.seller.application.sellerprofile.ApproveSellerProfile;
import vn.t3nexus.seller.application.sellerprofile.GetSellerProfileForReview;
import vn.t3nexus.seller.application.sellerprofile.ListPendingSellerProfiles;
import vn.t3nexus.seller.application.sellerprofile.RejectSellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;

import java.util.Objects;
import java.util.UUID;

/**
 * Bộ điều khiển REST dành cho quản trị viên xử lý hồ sơ người bán.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/seller-profiles")
public class AdminSellerProfileController {

    private final ListPendingSellerProfiles listPendingSellerProfiles;
    private final GetSellerProfileForReview getSellerProfileForReview;
    private final ApproveSellerProfile approveSellerProfile;
    private final RejectSellerProfile rejectSellerProfile;
    private final MeterRegistry meterRegistry;

    public AdminSellerProfileController(
            ListPendingSellerProfiles listPendingSellerProfiles,
            GetSellerProfileForReview getSellerProfileForReview,
            ApproveSellerProfile approveSellerProfile,
            RejectSellerProfile rejectSellerProfile,
            MeterRegistry meterRegistry
    ) {
        this.listPendingSellerProfiles = Objects.requireNonNull(listPendingSellerProfiles, "listPendingSellerProfiles");
        this.getSellerProfileForReview = Objects.requireNonNull(getSellerProfileForReview, "getSellerProfileForReview");
        this.approveSellerProfile = Objects.requireNonNull(approveSellerProfile, "approveSellerProfile");
        this.rejectSellerProfile = Objects.requireNonNull(rejectSellerProfile, "rejectSellerProfile");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @GetMapping
    public ApiResponse<PendingSellerProfilePageResponse> listPending(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);
        ListPendingSellerProfiles.Result result = listPendingSellerProfiles.handle(
                new ListPendingSellerProfiles.Query(actor, page, size)
        );
        return ApiResponse.ok(PendingSellerProfilePageResponse.from(result.page()));
    }

    @GetMapping("/{profileId}")
    public ApiResponse<AdminSellerProfileResponse> getForReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("profileId") UUID profileId
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);
        GetSellerProfileForReview.Result result = getSellerProfileForReview.handle(
                new GetSellerProfileForReview.Query(actor, profileId)
        );
        return ApiResponse.ok(AdminSellerProfileResponse.from(result.profile()));
    }

    @PostMapping("/{profileId}/approve")
    public ApiResponse<AdminSellerProfileResponse> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("profileId") UUID profileId,
            @RequestBody ApproveSellerProfileRequest request
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);
        Long version = request != null ? request.version() : null;

        ApproveSellerProfile.Result result = approveSellerProfile.handle(
                new ApproveSellerProfile.Command(actor, profileId, version)
        );

        SellerProfile profile = result.profile();
        log.info("SELLER_PROFILE_APPROVED profileId={} adminId={} version={}",
                profile.getId().getValue(), actor.accountId(), result.version());
        meterRegistry.counter("seller.profile.decision", "result", "approved").increment();

        return ApiResponse.ok(AdminSellerProfileResponse.from(profile, result.version()));
    }

    @PostMapping("/{profileId}/reject")
    public ApiResponse<AdminSellerProfileResponse> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("profileId") UUID profileId,
            @RequestBody RejectSellerProfileRequest request
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);
        Long version = request != null ? request.version() : null;
        String reason = request != null ? request.reason() : null;

        RejectSellerProfile.Result result = rejectSellerProfile.handle(
                new RejectSellerProfile.Command(actor, profileId, version, reason)
        );

        SellerProfile profile = result.profile();
        log.info("SELLER_PROFILE_REJECTED profileId={} adminId={} version={}",
                profile.getId().getValue(), actor.accountId(), result.version());
        meterRegistry.counter("seller.profile.decision", "result", "rejected").increment();

        return ApiResponse.ok(AdminSellerProfileResponse.from(profile, result.version()));
    }
}
