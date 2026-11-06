package vn.t3nexus.seller.presentation.sellerprofile;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.seller.application.sellerprofile.GetMySellerProfile;
import vn.t3nexus.seller.application.sellerprofile.ResubmitSellerProfile;
import vn.t3nexus.seller.application.sellerprofile.SubmitSellerProfile;
import vn.t3nexus.seller.application.sellerprofile.UpdateLegalInfo;
import vn.t3nexus.seller.application.sellerprofile.UpdateShopInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.util.Objects;

/**
 * Bộ điều khiển REST quản lý hồ sơ người bán.
 */
@Slf4j
@RestController
@RequestMapping("/api/seller/profile")
public class SellerProfileController {

    private final SubmitSellerProfile submitSellerProfile;
    private final ResubmitSellerProfile resubmitSellerProfile;
    private final UpdateShopInfo updateShopInfo;
    private final UpdateLegalInfo updateLegalInfo;
    private final GetMySellerProfile getMySellerProfile;
    private final MeterRegistry meterRegistry;

    public SellerProfileController(
            SubmitSellerProfile submitSellerProfile,
            ResubmitSellerProfile resubmitSellerProfile,
            UpdateShopInfo updateShopInfo,
            UpdateLegalInfo updateLegalInfo,
            GetMySellerProfile getMySellerProfile,
            MeterRegistry meterRegistry
    ) {
        this.submitSellerProfile = Objects.requireNonNull(submitSellerProfile, "submitSellerProfile");
        this.resubmitSellerProfile = Objects.requireNonNull(resubmitSellerProfile, "resubmitSellerProfile");
        this.updateShopInfo = Objects.requireNonNull(updateShopInfo, "updateShopInfo");
        this.updateLegalInfo = Objects.requireNonNull(updateLegalInfo, "updateLegalInfo");
        this.getMySellerProfile = Objects.requireNonNull(getMySellerProfile, "getMySellerProfile");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SellerProfileResponse> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody SubmitSellerProfileRequest request
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);

        SubmitSellerProfileRequest.LegalInfoRequest l = request != null ? request.legalInfo() : null;
        SubmitSellerProfileRequest.ShopRequest s = request != null ? request.shop() : null;
        SubmitSellerProfileRequest.ContactRequest c = s != null ? s.contact() : null;

        SellerType sellerType = PatchBodyReader.parseSellerType(request != null ? request.sellerType() : null);

        SubmitSellerProfile.Command command = new SubmitSellerProfile.Command(
                actor,
                sellerType,
                l != null ? l.legalName() : null,
                l != null ? l.representativeName() : null,
                l != null ? l.identityNumber() : null,
                l != null ? l.taxCode() : null,
                l != null ? l.businessAddress() : null,
                s != null ? s.shopName() : null,
                s != null ? s.description() : null,
                c != null ? c.personName() : null,
                c != null ? c.phone() : null,
                c != null ? c.address() : null
        );

        SubmitSellerProfile.Result result = submitSellerProfile.handle(command);

        log.info("SELLER_PROFILE_SUBMITTED profileId={} accountId={} resubmit=false",
                result.profile().getId().getValue(), actor.accountId());
        meterRegistry.counter("seller.profile.submitted", "kind", "first").increment();

        return ApiResponse.ok(SellerProfileResponse.from(result.profile()));
    }

    @PostMapping("/resubmit")
    public ApiResponse<SellerProfileResponse> resubmit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody SubmitSellerProfileRequest request
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);

        SubmitSellerProfileRequest.LegalInfoRequest l = request != null ? request.legalInfo() : null;
        SubmitSellerProfileRequest.ShopRequest s = request != null ? request.shop() : null;
        SubmitSellerProfileRequest.ContactRequest c = s != null ? s.contact() : null;

        SellerType sellerType = PatchBodyReader.parseSellerType(request != null ? request.sellerType() : null);

        ResubmitSellerProfile.Command command = new ResubmitSellerProfile.Command(
                actor,
                sellerType,
                l != null ? l.legalName() : null,
                l != null ? l.representativeName() : null,
                l != null ? l.identityNumber() : null,
                l != null ? l.taxCode() : null,
                l != null ? l.businessAddress() : null,
                s != null ? s.shopName() : null,
                s != null ? s.description() : null,
                c != null ? c.personName() : null,
                c != null ? c.phone() : null,
                c != null ? c.address() : null
        );

        ResubmitSellerProfile.Result result = resubmitSellerProfile.handle(command);

        log.info("SELLER_PROFILE_SUBMITTED profileId={} accountId={} resubmit=true",
                result.profile().getId().getValue(), actor.accountId());
        meterRegistry.counter("seller.profile.submitted", "kind", "resubmit").increment();

        return ApiResponse.ok(SellerProfileResponse.from(result.profile()));
    }

    @GetMapping
    public ApiResponse<SellerProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        SellerActor actor = SellerActorResolver.from(jwt);
        GetMySellerProfile.Result result = getMySellerProfile.handle(new GetMySellerProfile.Query(actor.accountId()));
        return ApiResponse.ok(SellerProfileResponse.from(result.profile()));
    }

    @PatchMapping("/shop-info")
    public ApiResponse<SellerProfileResponse> updateShop(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode body
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);
        PatchBodyReader.requireObject(body);

        JsonNode contact = PatchBodyReader.objectOrNull(body, "contact");

        UpdateShopInfo.Command command = new UpdateShopInfo.Command(
                actor,
                PatchBodyReader.text(body, "shopName"),
                PatchBodyReader.text(body, "description"),
                PatchBodyReader.text(contact, "personName"),
                PatchBodyReader.text(contact, "phone"),
                PatchBodyReader.text(contact, "address")
        );

        UpdateShopInfo.Result result = updateShopInfo.handle(command);

        if (result.changed()) {
            log.info("SELLER_PROFILE_UPDATED profileId={} accountId={} part=shop",
                    result.profile().getId().getValue(), actor.accountId());
        }

        return ApiResponse.ok(SellerProfileResponse.from(result.profile()));
    }

    @PatchMapping("/legal-info")
    public ApiResponse<SellerProfileResponse> updateLegal(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode body
    ) {
        SellerActor actor = SellerActorResolver.from(jwt);
        PatchBodyReader.requireObject(body);

        JsonNode legal = PatchBodyReader.objectOrNull(body, "legalInfo");

        UpdateLegalInfo.Command command = new UpdateLegalInfo.Command(
                actor,
                PatchBodyReader.sellerType(body, "sellerType"),
                PatchBodyReader.text(legal, "legalName"),
                PatchBodyReader.text(legal, "representativeName"),
                PatchBodyReader.text(legal, "identityNumber"),
                PatchBodyReader.text(legal, "taxCode"),
                PatchBodyReader.text(legal, "businessAddress")
        );

        UpdateLegalInfo.Result result = updateLegalInfo.handle(command);

        if (result.changed()) {
            log.info("SELLER_PROFILE_UPDATED profileId={} accountId={} part=legal",
                    result.profile().getId().getValue(), actor.accountId());
        }

        return ApiResponse.ok(SellerProfileResponse.from(result.profile()));
    }
}
