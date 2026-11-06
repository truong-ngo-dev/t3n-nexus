package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileSubmitted;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileUniqueness;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Use case xử lý nộp lại hồ sơ người bán đã bị từ chối.
 */
@Service
public class ResubmitSellerProfile implements CommandHandler<ResubmitSellerProfile.Command, ResubmitSellerProfile.Result> {

    private final SellerProfileRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final OutboxEventStore outboxEventStore;
    private final SellerProfileUniqueness uniqueness;

    public ResubmitSellerProfile(
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

        // (4) Nộp lại hồ sơ
        Instant now = clock.instant();
        profile.resubmit(command.actor(), legalInfo, shopInfo, now);

        // (5) Kiểm tra lại tính duy nhất, bỏ qua chính hồ sơ này
        uniqueness.requireShopNameAvailable(command.actor().accountId(), shopInfo.shopName());
        uniqueness.requireIdentityAvailable(command.actor().accountId(), legalInfo);

        // (6) Lưu hồ sơ vào kho
        repository.save(profile);

        // (7) Ghi sự kiện vào hộp thư outbox
        outboxEventStore.store(new SellerProfileSubmitted(
                idGenerator.generate(),
                now,
                command.actor().accountId(),
                profile.getId(),
                profile.submittedAt()
        ));

        // (8) Trả về kết quả
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
