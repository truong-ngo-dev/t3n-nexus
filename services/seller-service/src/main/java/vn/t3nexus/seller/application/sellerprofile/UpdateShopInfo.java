package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileUniqueness;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Clock;
import java.util.Objects;

/**
 * Use case xử lý cập nhật thông tin gian hàng của người bán.
 */
@Service
public class UpdateShopInfo implements CommandHandler<UpdateShopInfo.Command, UpdateShopInfo.Result> {

    private final SellerProfileRepository repository;
    private final Clock clock;

    public UpdateShopInfo(SellerProfileRepository repository, Clock clock) {
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

        // (3) Gộp thông tin mới vào giá trị hiện tại
        ShopInfo current = profile.shopInfo();
        String shopName = command.shopName().resolve(current.shopName().display());
        String description = command.description().resolve(current.description());
        String contactName = command.contactName().resolve(current.contact().personName());
        String contactPhone = command.contactPhone().resolve(current.contact().phone().value());
        String contactAddress = command.contactAddress().resolve(
                current.contact().address() == null ? null : current.contact().address().value()
        );

        ShopInfo newInfo = ShopInfo.of(
                shopName,
                description,
                Contact.of(contactName, contactPhone, contactAddress)
        );

        // (4) Cập nhật vào thực thể hồ sơ
        boolean changed = profile.updateShopInfo(command.actor(), newInfo, clock.instant());

        // (5) Nếu không thay đổi thì trả về ngay, không lưu, không ghi outbox
        if (!changed) {
            return new Result(profile, false);
        }

        // (6) Nếu có thay đổi, kiểm tra tính duy nhất của tên gian hàng và lưu
        new SellerProfileUniqueness(repository).requireShopNameAvailable(command.actor().accountId(), newInfo.shopName());
        repository.save(profile);

        return new Result(profile, true);
    }

    public record Command(
            SellerActor actor,
            Patch<String> shopName,
            Patch<String> description,
            Patch<String> contactName,
            Patch<String> contactPhone,
            Patch<String> contactAddress
    ) {}

    public record Result(SellerProfile profile, boolean changed) {}
}
