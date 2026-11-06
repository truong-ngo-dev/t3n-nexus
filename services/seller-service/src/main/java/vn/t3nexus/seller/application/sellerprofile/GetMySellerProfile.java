package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;

import java.util.Objects;
import java.util.UUID;

/**
 * Use case xem thông tin hồ sơ người bán của chính mình.
 */
@Service
public class GetMySellerProfile implements QueryHandler<GetMySellerProfile.Query, GetMySellerProfile.Result> {

    private final SellerProfileRepository repository;

    public GetMySellerProfile(SellerProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        Objects.requireNonNull(query, "query");
        SellerProfile profile = repository.findByOwner(query.accountId())
                .orElseThrow(() -> new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));
        return new Result(profile);
    }

    public record Query(UUID accountId) {}

    public record Result(SellerProfile profile) {}
}
