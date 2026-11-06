package vn.t3nexus.seller.application.sellerprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Use case từ chối hồ sơ người bán.
 */
@Service
public class RejectSellerProfile implements CommandHandler<RejectSellerProfile.Command, RejectSellerProfile.Result> {

    private final SellerProfileRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    public RejectSellerProfile(
            SellerProfileRepository repository,
            IdGenerator idGenerator,
            Clock clock,
            EventDispatcher eventDispatcher
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher");
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        Objects.requireNonNull(command, "command");

        // (1) Kiểm tra vai trò quản trị viên
        command.actor().requireAdmin();

        // (2) Kiểm tra phiên bản khác null trước khi đọc kho
        if (command.version() == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "version");
        }

        // (3) Đọc hồ sơ với khóa dòng
        SellerProfile profile = repository.findByIdForUpdate(SellerProfileId.of(command.profileId()))
                .orElseThrow(() -> new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));

        // (4) Miền thực hiện từ chối hồ sơ và phát sinh domain event
        UUID eventId = idGenerator.generate();
        Instant now = clock.instant();
        profile.reject(command.actor(), command.version(), command.reason(), eventId, now);

        // (5) Lưu vào kho dữ liệu
        repository.save(profile);

        // (6) Phát các domain event đồng bộ và dọn dẹp danh sách sự kiện
        eventDispatcher.dispatchAll(profile.getDomainEvents());
        profile.clearDomainEvents();

        // Mỗi quyết định tăng phiên bản đúng một (cùng quy tắc của domain event): trả phiên bản SAU quyết định
        return new Result(profile, profile.version() + 1);
    }

    public record Command(SellerActor actor, UUID profileId, Long version, String reason) {}

    public record Result(SellerProfile profile, long version) {}
}
