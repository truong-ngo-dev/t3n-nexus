package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileUniqueness;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * <p>Use case nộp hồ sơ đăng ký người bán lần đầu (<code>POST /profile</code>).</p>
 *
 * <p>Use case chỉ làm việc nghiệp vụ: dựng giá trị, kiểm trùng, tạo aggregate, lưu. Event {@code SellerProfileSubmitted} do
 * <b>aggregate phát</b>; sau khi lưu, use case đẩy event qua {@link EventDispatcher}, và handler
 * ({@code SellerProfileSubmittedHandler}) ghi hộp thư trong cùng giao dịch (ADR-0008). Cùng khuôn với duyệt và từ chối.</p>
 *
 * <p>Toàn bộ chạy trong một giao dịch: lỗi ở bất kỳ bước nào, kể cả vi phạm ràng buộc duy nhất lúc lưu (đua nhau), rollback cả
 * hồ sơ lẫn hàng hộp thư.</p>
 */
@Service
public class SubmitSellerProfile implements CommandHandler<SubmitSellerProfile.Command, SubmitSellerProfile.Result> {

    /** Kho hồ sơ; <code>save</code> dùng <code>saveAndFlush</code> nên vi phạm ràng buộc duy nhất nổ ngay trong giao dịch này. */
    private final SellerProfileRepository repository;

    /** Sinh mã UUID v7 cho hồ sơ và cho event (ADR-0001). */
    private final IdGenerator idGenerator;

    /** Nguồn thời gian; tiêm vào để test cố định được thời điểm nộp. */
    private final Clock clock;

    /** Đẩy event của aggregate tới các handler đồng bộ, trong cùng giao dịch; handler ném lỗi thì rollback cả giao dịch. */
    private final EventDispatcher eventDispatcher;

    /** Kiểm "một chủ, một tên, một khóa định danh hiệu lực" bằng cách hỏi kho; chốt cuối là ràng buộc duy nhất ở cơ sở dữ liệu. */
    private final SellerProfileUniqueness uniqueness;

    /**
     * Dựng use case; <code>uniqueness</code> được tạo từ chính <code>repository</code> chứ không tiêm riêng, vì nó là
     * quy tắc miền thuần chỉ cần kho.
     *
     * @throws NullPointerException nếu một phụ thuộc là <code>null</code>
     */
    public SubmitSellerProfile(
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
     * <p>Thứ tự có chủ ý: vai trước mọi thứ (tài khoản <code>ADMIN</code> nhận <code>SELLER_ROLE_NOT_ALLOWED</code> dù dữ liệu sai),
     * rồi dựng và kiểm định dạng (lỗi nêu tên trường, không nêu giá trị), rồi kiểm trùng (báo sớm), rồi tạo aggregate và lưu
     * (chốt cuối), cuối cùng mới phát event.</p>
     *
     * <p>Mã hồ sơ được sinh trước mã event; test dựa vào thứ tự này.</p>
     *
     * @throws vn.t3nexus.lib.common.domain.exception.DomainException <code>SELLER_ROLE_NOT_ALLOWED</code>, <code>SELLER_PROFILE_EXISTS</code>,
     *         <code>SHOP_NAME_TAKEN</code>, <code>IDENTITY_ALREADY_REGISTERED</code>
     * @throws vn.t3nexus.seller.domain.sellerprofile.SellerFieldException dữ liệu sai định dạng hoặc thiếu theo loại hình
     */
    @Override
    @Transactional
    public Result handle(Command command) {
        // (1) Kiểm tra vai trò người dùng
        command.actor().requireUser();

        // (2) Dựng thông tin pháp lý và thông tin gian hàng
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

        // (3) Kiểm tra tính duy nhất
        uniqueness.requireCanSubmit(command.actor().accountId(), shopInfo.shopName(), legalInfo);

        // (4) Tạo thực thể hồ sơ người bán; aggregate tự phát SellerProfileSubmitted
        Instant now = clock.instant();
        SellerProfileId profileId = SellerProfileId.of(idGenerator.generate());
        UUID eventId = idGenerator.generate();
        SellerProfile profile = SellerProfile.submit(profileId, command.actor(), legalInfo, shopInfo, eventId, now);

        // (5) Lưu vào kho dữ liệu
        repository.save(profile);

        // (6) Phát các domain event đồng bộ (handler ghi hộp thư cùng giao dịch) rồi dọn danh sách
        eventDispatcher.dispatchAll(profile.getDomainEvents());
        profile.clearDomainEvents();

        // (7) Trả về kết quả
        return new Result(profile);
    }

    /**
     * Lệnh nộp hồ sơ: dữ liệu thô từ lớp trình bày, chưa chuẩn hóa hay kiểm định dạng (việc của miền).
     *
     * @param actor              người nộp, dựng từ token (mã tài khoản và vai), không bao giờ từ nội dung yêu cầu
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
     * Kết quả nộp.
     *
     * @param profile hồ sơ vừa tạo, ở <code>PENDING_REVIEW</code>; danh sách event của nó đã được dọn sau khi phát
     */
    public record Result(SellerProfile profile) {}
}
