package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileUniqueness;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.time.Clock;
import java.util.Objects;

/**
 * Use case xử lý cập nhật thông tin pháp lý của người bán.
 */
@Service
public class UpdateLegalInfo implements CommandHandler<UpdateLegalInfo.Command, UpdateLegalInfo.Result> {

    private final SellerProfileRepository repository;
    private final Clock clock;

    public UpdateLegalInfo(SellerProfileRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        // (1) Kiểm tra vai trò người dùng
        command.actor().requireUser();

        // (2) Tìm hồ sơ của chủ sở hữu
        SellerProfile profile = repository.findByOwner(command.actor().accountId())
                .orElseThrow(() -> new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));

        // (3) Gộp thông tin pháp lý mới vào giá trị hiện tại
        LegalInfo current = profile.legalInfo();
        SellerType sellerType = command.sellerType().resolve(current.type());
        String legalName = command.legalName().resolve(current.legalName());
        String representativeName = command.representativeName().resolve(current.representativeName());
        String identityNumber = command.identityNumber().resolve(current.identityNumber().value());
        String taxCode = command.taxCode().resolve(
                current.taxCode() == null ? null : current.taxCode().value()
        );
        String businessAddress = command.businessAddress().resolve(current.businessAddress().value());

        LegalInfo newInfo = LegalInfo.of(
                sellerType,
                legalName,
                representativeName,
                identityNumber,
                taxCode,
                businessAddress
        );

        // (4) Cập nhật vào thực thể hồ sơ
        boolean changed = profile.updateLegalInfo(command.actor(), newInfo, clock.instant());

        // (5) Nếu không thay đổi thì trả về ngay, không lưu, không ghi outbox
        if (!changed) {
            return new Result(profile, false);
        }

        // (6) Nếu có thay đổi, kiểm tra tính duy nhất của thông tin định danh và lưu
        new SellerProfileUniqueness(repository).requireIdentityAvailable(command.actor().accountId(), newInfo);
        repository.save(profile);

        return new Result(profile, true);
    }

    public record Command(
            SellerActor actor,
            Patch<SellerType> sellerType,
            Patch<String> legalName,
            Patch<String> representativeName,
            Patch<String> identityNumber,
            Patch<String> taxCode,
            Patch<String> businessAddress
    ) {}

    public record Result(SellerProfile profile, boolean changed) {}
}
