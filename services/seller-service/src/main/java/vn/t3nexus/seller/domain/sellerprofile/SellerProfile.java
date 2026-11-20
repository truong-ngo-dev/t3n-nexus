package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * <p>Gốc tập hợp hồ sơ người bán: một tài khoản có tối đa một hồ sơ, đi qua các trạng thái
 * <code>PENDING_REVIEW</code>, <code>APPROVED</code>, <code>REJECTED</code> (xem {@link SellerProfileStatus}).</p>
 *
 * <p>Mọi chuyển đổi trạng thái đều qua phương thức của lớp này, không có setter. Các chuyển đổi cần báo cho bên ngoài
 * (<code>submit</code>, <code>resubmit</code>, <code>approve</code>, <code>reject</code>) <b>phát domain event vào chính aggregate</b>
 * bằng <code>addDomainEvent</code>; use case lưu rồi đẩy các event đó qua <code>EventDispatcher</code>, và handler ghi hộp thư
 * (ADR-0008) trong cùng giao dịch. Use case không tự gọi hộp thư.</p>
 */
public final class SellerProfile extends AbstractAggregateRoot<SellerProfileId> {

    /** Độ dài tối đa của lý do từ chối, sau khi bỏ khoảng trắng hai đầu. */
    private static final int MAX_REASON_LENGTH = 500;

    /** Mã tài khoản chủ hồ sơ; lấy từ token lúc nộp, không đổi suốt vòng đời. */
    private final UUID ownerAccountId;

    /** Trạng thái hiện tại; chỉ đổi qua <code>approve</code>, <code>reject</code>, <code>resubmit</code>. */
    private SellerProfileStatus status;

    /** Thông tin pháp lý (gồm loại hình); bị khóa sau khi <code>APPROVED</code>. */
    private LegalInfo legalInfo;

    /** Thông tin gian hàng (tên, mô tả, liên hệ); sửa được khi chờ duyệt hoặc đã duyệt. */
    private ShopInfo shopInfo;

    /** Lý do từ chối gần nhất; <code>null</code> trừ khi hồ sơ đang <code>REJECTED</code> (nộp lại thì xóa). */
    private String rejectionReason;

    /** Thời điểm nộp hoặc nộp lại gần nhất, đã cắt micro giây (khớp độ chính xác của cột lưu trữ). */
    private Instant submittedAt;

    /** Thời điểm quản trị quyết định gần nhất; <code>null</code> khi chưa quyết định hoặc sau khi nộp lại. */
    private Instant reviewedAt;

    /** Mã tài khoản quản trị đã quyết định gần nhất; <code>null</code> cùng điều kiện với <code>reviewedAt</code>. */
    private UUID reviewedBy;

    /** Thời điểm tạo hồ sơ (lần nộp đầu), đã cắt micro giây; không đổi. */
    private final Instant createdAt;

    /** Thời điểm đổi gần nhất, đã cắt micro giây. */
    private Instant updatedAt;

    /**
     * Phiên bản khóa lạc quan do lớp lưu trữ quản lý (ADR-0011). <code>null</code> khi hồ sơ mới dựng và chưa được lưu;
     * lệnh duyệt và từ chối cần giá trị này nên ném lỗi nếu còn <code>null</code>.
     */
    private final Long version;

    /**
     * Chỉ gọi từ <code>submit</code> và <code>restore</code>: hai đường vào đã kiểm tham số theo nhu cầu riêng, nên hàm dựng này
     * không kiểm lại mà chỉ gán. Giữ private để không có đường nào tạo hồ sơ mà bỏ qua các kiểm đó.
     */
    private SellerProfile(
            SellerProfileId id,
            UUID ownerAccountId,
            SellerProfileStatus status,
            LegalInfo legalInfo,
            ShopInfo shopInfo,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewedAt,
            UUID reviewedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        super.setId(id);
        this.ownerAccountId = ownerAccountId;
        this.status = status;
        this.legalInfo = legalInfo;
        this.shopInfo = shopInfo;
        this.rejectionReason = rejectionReason;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Mã hồ sơ gán một lần ở hàm dựng; gọi lại là lỗi lập trình.
     *
     * @throws UnsupportedOperationException luôn luôn
     */
    @Override
    public void setId(SellerProfileId id) {
        throw new UnsupportedOperationException("Hồ sơ người bán không đổi mã");
    }

    /**
     * <p>Tạo hồ sơ mới ở trạng thái <code>PENDING_REVIEW</code>, phiên bản chưa có (do lớp lưu trữ gán khi lưu).</p>
     *
     * <p><b>Phát một {@link SellerProfileSubmitted}</b> vào aggregate: use case phải đẩy event này đi sau khi lưu thành công.
     * Vai được kiểm trước mọi kiểm khác, nên tài khoản <code>ADMIN</code> luôn nhận <code>SELLER_ROLE_NOT_ALLOWED</code>
     * kể cả khi các tham số khác sai.</p>
     *
     * @param id        mã hồ sơ, do use case sinh
     * @param actor     người thực hiện; phải là <code>USER</code>, và là chủ hồ sơ
     * @param eventId   mã của {@link SellerProfileSubmitted} sẽ phát; do use case sinh sau mã hồ sơ
     * @param now       thời điểm nộp; lưu cắt micro giây, còn <code>occurredOn</code> của event giữ nguyên giá trị này
     * @throws NullPointerException nếu một tham số là <code>null</code>
     * @throws DomainException      <code>SELLER_ROLE_NOT_ALLOWED</code> nếu <code>actor</code> không phải <code>USER</code>
     */
    public static SellerProfile submit(
            SellerProfileId id,
            SellerActor actor,
            LegalInfo legalInfo,
            ShopInfo shopInfo,
            UUID eventId,
            Instant now
    ) {
        Objects.requireNonNull(actor, "actor");
        actor.requireUser();

        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(legalInfo, "legalInfo");
        Objects.requireNonNull(shopInfo, "shopInfo");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now, "now");

        Instant truncatedNow = now.truncatedTo(ChronoUnit.MICROS);

        SellerProfile profile = new SellerProfile(
                id,
                actor.accountId(),
                SellerProfileStatus.PENDING_REVIEW,
                legalInfo,
                shopInfo,
                null,
                truncatedNow,
                null,
                null,
                truncatedNow,
                truncatedNow,
                null
        );

        profile.addDomainEvent(new SellerProfileSubmitted(eventId, now, actor.accountId(), id, truncatedNow));
        return profile;
    }

    /**
     * <p>Dựng lại hồ sơ từ dữ liệu đã lưu. Không kiểm vai hay nghiệp vụ (dữ liệu đã hợp lệ lúc lưu) và <b>không phát event nào</b>:
     * dựng lại không phải một sự kiện nghiệp vụ, phát lại sẽ đẩy trùng event đã đi từ lần nộp trước.</p>
     *
     * @param version phiên bản khóa lạc quan đang lưu; có thể <code>null</code> trong test dựng sẵn
     * @throws NullPointerException nếu một tham số bắt buộc (mã, chủ, trạng thái, thông tin pháp lý, gian hàng,
     *                              <code>submittedAt</code>, <code>createdAt</code>, <code>updatedAt</code>) là <code>null</code>
     */
    public static SellerProfile restore(
            SellerProfileId id,
            UUID ownerAccountId,
            SellerProfileStatus status,
            LegalInfo legalInfo,
            ShopInfo shopInfo,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewedAt,
            UUID reviewedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerAccountId, "ownerAccountId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(legalInfo, "legalInfo");
        Objects.requireNonNull(shopInfo, "shopInfo");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        return new SellerProfile(
                id,
                ownerAccountId,
                status,
                legalInfo,
                shopInfo,
                rejectionReason,
                submittedAt,
                reviewedAt,
                reviewedBy,
                createdAt,
                updatedAt,
                version
        );
    }

    /**
     * <p>Duyệt hồ sơ. Kiểm theo thứ tự: vai quản trị, trạng thái <code>PENDING_REVIEW</code>, hồ sơ đã lưu (có phiên bản),
     * rồi phiên bản mà quản trị đã xem phải khớp phiên bản hiện tại. Phát {@link SellerProfileApproved} mang phiên bản
     * <b>sau</b> quyết định (<code>version + 1</code>, vì lần lưu kế tiếp tăng phiên bản đúng một).</p>
     *
     * @param viewedVersion phiên bản hồ sơ lúc quản trị xem chi tiết
     * @param eventId       mã của event phát ra, do use case sinh
     * @throws DomainException <code>SELLER_ROLE_NOT_ALLOWED</code>, <code>INVALID_STATE</code> hoặc <code>STALE_VERSION</code>
     * @throws IllegalStateException nếu hồ sơ chưa được lưu
     */
    public void approve(SellerActor actor, long viewedVersion, UUID eventId, Instant now) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now, "now");

        actor.requireAdmin();

        if (status != SellerProfileStatus.PENDING_REVIEW) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (version == null) {
            throw new IllegalStateException("Hồ sơ chưa được lưu, không có phiên bản");
        }

        if (viewedVersion != version) {
            throw new DomainException(SellerErrorCode.STALE_VERSION);
        }

        Instant truncated = now.truncatedTo(ChronoUnit.MICROS);
        this.status = SellerProfileStatus.APPROVED;
        this.reviewedAt = truncated;
        this.reviewedBy = actor.accountId();
        this.updatedAt = truncated;

        addDomainEvent(new SellerProfileApproved(
                eventId,
                now,
                ownerAccountId,
                getId(),
                version + 1,
                actor.accountId(),
                viewedVersion
        ));
    }

    /**
     * <p>Từ chối hồ sơ kèm lý do. Cùng thứ tự kiểm với {@link #approve}, thêm kiểm lý do (bắt buộc, tối đa
     * {@value #MAX_REASON_LENGTH} ký tự sau khi bỏ khoảng trắng hai đầu) ngay sau kiểm vai. Phát
     * {@link SellerProfileRejected} mang lý do đã bỏ khoảng trắng.</p>
     *
     * @param reason lý do hiển thị cho người bán
     * @throws SellerFieldException <code>REJECTION_REASON_REQUIRED</code> hoặc <code>SELLER_FIELD_INVALID</code> (lý do quá dài)
     * @throws DomainException      <code>SELLER_ROLE_NOT_ALLOWED</code>, <code>INVALID_STATE</code> hoặc <code>STALE_VERSION</code>
     */
    public void reject(SellerActor actor, long viewedVersion, String reason, UUID eventId, Instant now) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now, "now");

        actor.requireAdmin();

        if (reason == null || reason.isBlank()) {
            throw new SellerFieldException(SellerErrorCode.REJECTION_REASON_REQUIRED, "reason");
        }

        String trimmed = reason.strip();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "reason");
        }

        if (status != SellerProfileStatus.PENDING_REVIEW) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (version == null) {
            throw new IllegalStateException("Hồ sơ chưa được lưu, không có phiên bản");
        }

        if (viewedVersion != version) {
            throw new DomainException(SellerErrorCode.STALE_VERSION);
        }

        Instant truncated = now.truncatedTo(ChronoUnit.MICROS);
        this.status = SellerProfileStatus.REJECTED;
        this.rejectionReason = trimmed;
        this.reviewedAt = truncated;
        this.reviewedBy = actor.accountId();
        this.updatedAt = truncated;

        addDomainEvent(new SellerProfileRejected(
                eventId,
                now,
                ownerAccountId,
                getId(),
                version + 1,
                actor.accountId(),
                viewedVersion,
                trimmed
        ));
    }

    /**
     * Sửa thông tin gian hàng; cho phép khi <code>PENDING_REVIEW</code> hoặc <code>APPROVED</code>. Không phát event
     * (chưa bên nào cần biết, xem <code>03/tech.md</code>).
     *
     * @return <code>true</code> nếu có thay đổi; <code>false</code> khi giá trị mới bằng giá trị hiện tại (không đụng
     *         <code>updatedAt</code>, để lớp lưu trữ không tăng phiên bản vô ích)
     * @throws DomainException <code>SELLER_ROLE_NOT_ALLOWED</code>; <code>SELLER_PROFILE_NOT_FOUND</code> nếu người gọi không phải chủ
     *                         (không lộ sự tồn tại của hồ sơ người khác); <code>INVALID_STATE</code> khi <code>REJECTED</code>
     */
    public boolean updateShopInfo(SellerActor actor, ShopInfo newInfo, Instant now) {
        actor.requireUser();
        if (!actor.accountId().equals(ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND);
        }
        if (status == SellerProfileStatus.REJECTED) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (newInfo.equals(this.shopInfo)) {
            return false;
        }

        this.shopInfo = newInfo;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    /**
     * Sửa thông tin pháp lý và loại hình; chỉ cho phép khi <code>PENDING_REVIEW</code>. Không phát event.
     *
     * @return <code>true</code> nếu có thay đổi, <code>false</code> nếu giá trị mới bằng giá trị hiện tại
     * @throws DomainException <code>SELLER_ROLE_NOT_ALLOWED</code>; <code>SELLER_PROFILE_NOT_FOUND</code> nếu không phải chủ;
     *                         <code>LEGAL_INFO_LOCKED</code> khi <code>APPROVED</code>; <code>INVALID_STATE</code> khi <code>REJECTED</code>
     */
    public boolean updateLegalInfo(SellerActor actor, LegalInfo newInfo, Instant now) {
        actor.requireUser();
        if (!actor.accountId().equals(ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND);
        }
        if (status == SellerProfileStatus.APPROVED) {
            throw new DomainException(SellerErrorCode.LEGAL_INFO_LOCKED);
        }
        if (status == SellerProfileStatus.REJECTED) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (newInfo.equals(this.legalInfo)) {
            return false;
        }

        this.legalInfo = newInfo;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    /**
     * <p>Sửa và nộp lại hồ sơ bị từ chối: thay <b>toàn bộ</b> thông tin pháp lý và gian hàng, về <code>PENDING_REVIEW</code>,
     * xóa lý do từ chối và dấu vết quyết định (<code>reviewedAt</code>, <code>reviewedBy</code>), ghi lại thời điểm nộp.</p>
     *
     * <p><b>Phát một {@link SellerProfileSubmitted}</b> (cùng loại event với lần nộp đầu). Mọi kiểm đều chạy trước khi đổi
     * trạng thái hay phát event, nên nộp lại bị từ chối thì hồ sơ không đổi và không để lại event nào chờ phát.</p>
     *
     * @param eventId mã của event phát ra, do use case sinh
     * @param now     thời điểm nộp lại; lưu cắt micro giây, còn <code>occurredOn</code> của event giữ nguyên giá trị này
     * @throws NullPointerException <code>eventId</code> là <code>null</code>
     * @throws DomainException      <code>SELLER_ROLE_NOT_ALLOWED</code>; <code>SELLER_PROFILE_NOT_FOUND</code> nếu không phải chủ;
     *                              <code>INVALID_STATE</code> nếu hồ sơ không ở <code>REJECTED</code>
     */
    public void resubmit(SellerActor actor, LegalInfo newLegalInfo, ShopInfo newShopInfo, UUID eventId, Instant now) {
        Objects.requireNonNull(eventId, "eventId");
        actor.requireUser();
        if (!actor.accountId().equals(ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND);
        }
        if (status != SellerProfileStatus.REJECTED) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        Instant truncatedNow = now.truncatedTo(ChronoUnit.MICROS);
        this.legalInfo = newLegalInfo;
        this.shopInfo = newShopInfo;
        this.status = SellerProfileStatus.PENDING_REVIEW;
        this.rejectionReason = null;
        this.reviewedAt = null;
        this.reviewedBy = null;
        this.submittedAt = truncatedNow;
        this.updatedAt = truncatedNow;

        addDomainEvent(new SellerProfileSubmitted(eventId, now, ownerAccountId, getId(), truncatedNow));
    }

    /** @return phiên bản khóa lạc quan; <code>null</code> khi hồ sơ chưa được lưu */
    public Long version() {
        return version;
    }

    public UUID ownerAccountId() {
        return ownerAccountId;
    }

    public SellerProfileStatus status() {
        return status;
    }

    public SellerType sellerType() {
        return legalInfo.type();
    }

    public LegalInfo legalInfo() {
        return legalInfo;
    }

    public ShopInfo shopInfo() {
        return shopInfo;
    }

    /** @return lý do từ chối gần nhất, hoặc <code>null</code> khi hồ sơ không ở <code>REJECTED</code> */
    public String rejectionReason() {
        return rejectionReason;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    /** @return thời điểm quản trị quyết định gần nhất, hoặc <code>null</code> khi chưa có quyết định hiệu lực */
    public Instant reviewedAt() {
        return reviewedAt;
    }

    /** @return mã quản trị đã quyết định gần nhất, hoặc <code>null</code> cùng điều kiện với {@link #reviewedAt()} */
    public UUID reviewedBy() {
        return reviewedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
