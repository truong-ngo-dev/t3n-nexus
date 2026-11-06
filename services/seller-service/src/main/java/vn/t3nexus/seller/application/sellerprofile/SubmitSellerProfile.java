package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileSubmitted;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileUniqueness;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Use case xử lý nộp hồ sơ đăng ký người bán mới.
 */
@Service
public class SubmitSellerProfile implements CommandHandler<SubmitSellerProfile.Command, SubmitSellerProfile.Result> {

    private final SellerProfileRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final OutboxEventStore outboxEventStore;
    private final SellerProfileUniqueness uniqueness;

    public SubmitSellerProfile(
            SellerProfileRepository repository,
            IdGenerator idGenerator,
            Clock clock,
            OutboxEventStore outboxEventStore
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.outboxEventStore = Objects.requireNonNull(outboxEventStore, "outboxEventStore");
        this.uniqueness = new SellerProfileUniqueness(repository);
    }

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

        // (4) Tạo thực thể hồ sơ người bán
        Instant now = clock.instant();
        SellerProfile profile = SellerProfile.submit(
                SellerProfileId.of(idGenerator.generate()),
                command.actor(),
                legalInfo,
                shopInfo,
                now
        );

        // (5) Lưu vào kho dữ liệu
        repository.save(profile);

        // (6) Ghi sự kiện vào hộp thư outbox
        outboxEventStore.store(new SellerProfileSubmitted(
                idGenerator.generate(),
                now,
                command.actor().accountId(),
                profile.getId(),
                profile.submittedAt()
        ));

        // (7) Trả về kết quả
        return new Result(profile);
    }

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

    public record Result(SellerProfile profile) {}
}
