package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileUniqueness;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * <p>Use case sửa và nộp lại hồ sơ người bán đã bị từ chối (<code>POST /profile/resubmit</code>); gửi <b>toàn bộ</b> trường, kể cả
 * loại hình và thông tin pháp lý.</p>
 *
 * <p>Như {@link SubmitSellerProfile}: use case chỉ làm việc nghiệp vụ, <code>SellerProfile.resubmit</code> phát
 * {@code SellerProfileSubmitted}, và sau khi lưu use case đẩy event qua {@link EventDispatcher}; handler ghi hộp thư cùng giao dịch.</p>
 */
@Service
public class ResubmitSellerProfile implements CommandHandler<ResubmitSellerProfile.Command, ResubmitSellerProfile.Result> {

    /** Kho hồ sơ; tìm theo chủ và lưu bằng <code>saveAndFlush</code> nên vi phạm ràng buộc duy nhất nổ ngay trong giao dịch này. */
    private final SellerProfileRepository repository;

    /** Sinh mã UUID v7 cho event (hồ sơ đã có mã từ lần nộp đầu) (ADR-0001). */
    private final IdGenerator idGenerator;

    /** Nguồn thời gian; tiêm vào để test cố định được thời điểm nộp lại. */
    private final Clock clock;

    /** Đẩy event của aggregate tới các handler đồng bộ, trong cùng giao dịch; handler ném lỗi thì rollback cả giao dịch. */
    private final EventDispatcher eventDispatcher;

    /** Kiểm trùng tên và khóa định danh <b>bỏ qua chính hồ sơ này</b>, để nộp lại giữ nguyên tên hay giấy tờ cũ vẫn được. */
    private final SellerProfileUniqueness uniqueness;

    /**
     * Dựng use case; <code>uniqueness</code> được tạo từ chính <code>repository</code> chứ không tiêm riêng.
     *
     * @throws NullPointerException nếu một phụ thuộc là <code>null</code>
     */
    public ResubmitSellerProfile(
            SellerProfileRepository repository,
            IdGenerator idGenerator,
            Clock clock,
            EventDispatcher eventDispatcher
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher");
        this.uniqueness = new SellerProfileUniqueness(repository);
    }

    /**
     * <p>Thứ tự có chủ ý: vai, tìm hồ sơ của chủ, dựng và kiểm định dạng, rồi để aggregate kiểm trạng thái và đổi trạng thái,
     * rồi kiểm trùng lại (người khác có thể đã lấy tên hoặc giấy tờ trong lúc hồ sơ bị từ chối), rồi lưu, cuối cùng phát event.</p>
     *
     * <p>Event chỉ được phát sau khi lưu thành công; lỗi trùng hay lỗi lưu ném ra trước đó nên không có event nào đi, và
     * giao dịch rollback.</p>
     *
     * @throws DomainException <code>SELLER_ROLE_NOT_ALLOWED</code>, <code>SELLER_PROFILE_NOT_FOUND</code> (chưa có hồ sơ),
     *         <code>INVALID_STATE</code> (hồ sơ không ở <code>REJECTED</code>), <code>SHOP_NAME_TAKEN</code>,
     *         <code>IDENTITY_ALREADY_REGISTERED</code>
     * @throws vn.t3nexus.seller.domain.sellerprofile.SellerFieldException dữ liệu sai định dạng hoặc thiếu theo loại hình
     */
    @Override
    @Transactional
    public Result handle(Command command) {
        // (1) Kiểm tra vai trò người dùng
        command.actor().requireUser();

        // (2) Tìm hồ sơ hiện tại của chủ sở hữu
        SellerProfile profile = repository.findByOwner(command.actor().accountId())
                .orElseThrow(() -> new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));

        // (3) Dựng LegalInfo và ShopInfo
        LegalInfo legalInfo = LegalInfo.of(
                command.sellerType(),
                command.legalName(),
                command.representativeName(),
                command.identityNumber(),
                command.taxCode(),
                command.businessAddress()
        );
        Contact contact = Contact.of(
                command.contactName(),
                command.contactPhone(),
                command.contactAddress()
        );
        ShopInfo shopInfo = ShopInfo.of(
                command.shopName(),
                command.description(),
                contact
        );

        // (4) Nộp lại hồ sơ; aggregate tự phát SellerProfileSubmitted
        Instant now = clock.instant();
        UUID eventId = idGenerator.generate();
        profile.resubmit(command.actor(), legalInfo, shopInfo, eventId, now);

        // (5) Kiểm tra lại tính duy nhất, bỏ qua chính hồ sơ này
        uniqueness.requireShopNameAvailable(command.actor().accountId(), shopInfo.shopName());
        uniqueness.requireIdentityAvailable(command.actor().accountId(), legalInfo);

        // (6) Lưu hồ sơ vào kho
        repository.save(profile);

        // (7) Phát các domain event đồng bộ (handler ghi hộp thư cùng giao dịch) rồi dọn danh sách
        eventDispatcher.dispatchAll(profile.getDomainEvents());
        profile.clearDomainEvents();

        // (8) Trả về kết quả
        return new Result(profile);
    }

    /**
     * Lệnh nộp lại: cùng khuôn dữ liệu thô với {@link SubmitSellerProfile.Command}, gửi toàn bộ trường.
     *
     * @param actor              người nộp lại, dựng từ token; phải là chủ hồ sơ
     * @param representativeName người đại diện; chỉ có nghĩa với doanh nghiệp, loại hình khác thì miền bỏ qua
     * @param taxCode            mã số thuế; bắt buộc với hộ và doanh nghiệp, tùy chọn với cá nhân
     * @param description        mô tả gian hàng, tùy chọn
     * @param contactAddress     địa chỉ liên hệ, tùy chọn; rỗng thì coi là địa chỉ kinh doanh
     */
    public record Command(
            SellerActor actor,
            SellerType sellerType,
            String legalName,
            String representativeName,
            String identityNumber,
            String taxCode,
            String businessAddress,
            String shopName,
            String description,
            String contactName,
            String contactPhone,
            String contactAddress
    ) {}

    /**
     * Kết quả nộp lại.
     *
     * @param profile hồ sơ sau khi nộp lại, ở <code>PENDING_REVIEW</code>; danh sách event của nó đã được dọn sau khi phát
     */
    public record Result(SellerProfile profile) {}
}
