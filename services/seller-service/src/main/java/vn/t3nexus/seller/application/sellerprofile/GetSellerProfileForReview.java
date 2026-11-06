package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;

import java.util.Objects;
import java.util.UUID;

/**
 * Use case lấy thông tin chi tiết hồ sơ người bán cho quản trị viên xem xét.
 */
@Service
public class GetSellerProfileForReview implements QueryHandler<GetSellerProfileForReview.Query, GetSellerProfileForReview.Result> {

    private final SellerProfileRepository repository;

    public GetSellerProfileForReview(SellerProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query q) {
        Objects.requireNonNull(q, "query");

        // (1) Kiểm tra vai trò quản trị viên
        q.actor().requireAdmin();

        // (2) Đọc hồ sơ từ kho dữ liệu
        SellerProfile profile = repository.findById(SellerProfileId.of(q.profileId()))
                .orElseThrow(() -> new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));

        return new Result(profile);
    }

    public record Query(SellerActor actor, UUID profileId) {}

    public record Result(SellerProfile profile) {}
}
