package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;

import java.util.Objects;

/**
 * Use case xem danh sách hồ sơ người bán đang chờ duyệt.
 */
@Service
public class ListPendingSellerProfiles implements QueryHandler<ListPendingSellerProfiles.Query, ListPendingSellerProfiles.Result> {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 50;

    private final PendingSellerProfileQuery query;

    public ListPendingSellerProfiles(PendingSellerProfileQuery query) {
        this.query = Objects.requireNonNull(query, "query");
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query q) {
        Objects.requireNonNull(q, "query");

        // (1) Kiểm tra vai trò quản trị viên
        q.actor().requireAdmin();

        // (2) Kiểm tra hợp lệ trang và kích thước trang (kiểm page trước size)
        if (q.page() < 0) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "page");
        }
        if (q.size() < MIN_SIZE || q.size() > MAX_SIZE) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "size");
        }

        // (3) Truy vấn danh sách hồ sơ chờ duyệt
        PendingSellerProfileQuery.Result pageResult = query.find(q.page(), q.size());
        return new Result(pageResult);
    }

    public record Query(SellerActor actor, int page, int size) {}

    public record Result(PendingSellerProfileQuery.Result page) {}
}
