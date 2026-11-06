package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.NameKey;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

/**
 * Nhãn của sản phẩm (INV-CAT-20, 21). {@code name} là tên hiển thị, đổi được; {@code nameKey} là bản chuẩn hóa tự sinh
 * để chống trùng (bỏ dấu, chữ thường, khoảng trắng). Thương hiệu không bị xóa, chỉ ngừng dùng.
 * <br>Mọi hành vi ghi nhận {@link ChangeContext} và phát {@link BrandChanged}; phiên bản trong event là phiên bản SAU
 * thay đổi ({@code version() + 1}, hoặc {@code 0} khi tạo mới).
 */
public class Brand extends AbstractAggregateRoot<BrandId> implements AggregateRoot<BrandId> {

    public static final int NAME_MAX_LENGTH = 100;

    private String name;
    private String nameKey;
    private BrandStatus status;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Brand(BrandId id, String name, String nameKey, BrandStatus status, Long version,
                  Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name      = name;
        this.nameKey   = nameKey;
        this.status    = status;
        this.version   = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ───────────── Factory ─────────────

    /** Khóa so trùng của tên; ném 400 nếu tên rỗng hoặc quá dài. Use case dùng để kiểm sớm tên đã có. */
    public static String nameKeyOf(String name) {
        return normalizedName(name).key();
    }

    public static Brand create(BrandId id, String name, ChangeContext ctx) {
        NameKey normalized = normalizedName(name);
        Brand brand = new Brand(id, normalized.display(), normalized.key(), BrandStatus.ACTIVE, null,
                ctx.now(), ctx.now());
        brand.emit(ctx, BrandChanged.Change.CREATED, brand.name, null);
        return brand;
    }

    public static Brand reconstitute(BrandId id, String name, String nameKey, BrandStatus status, Long version,
                                     Instant createdAt, Instant updatedAt) {
        return new Brand(id, name, nameKey, status, version, createdAt, updatedAt);
    }

    // ───────────── Hành vi ─────────────

    /** Đổi tên hiển thị, áp ngay mọi sản phẩm đang dùng; làm được cả khi đã ngừng dùng. */
    public void rename(String newName, ChangeContext ctx) {
        NameKey normalized = normalizedName(newName);
        String oldName = this.name;
        this.name      = normalized.display();
        this.nameKey   = normalized.key();
        this.updatedAt = ctx.now();
        emit(ctx, BrandChanged.Change.RENAMED, this.name, oldName);
    }

    public void deactivate(ChangeContext ctx) {
        if (status == BrandStatus.INACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status    = BrandStatus.INACTIVE;
        this.updatedAt = ctx.now();
        emit(ctx, BrandChanged.Change.DEACTIVATED, null, null);
    }

    public void activate(ChangeContext ctx) {
        if (status == BrandStatus.ACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status    = BrandStatus.ACTIVE;
        this.updatedAt = ctx.now();
        emit(ctx, BrandChanged.Change.ACTIVATED, null, null);
    }

    public boolean isActive() {
        return status == BrandStatus.ACTIVE;
    }

    /** Phiên bản SAU thay đổi sắp lưu: {@code 0} khi chưa lưu, ngược lại {@code version() + 1}. */
    public long nextVersion() {
        return version == null ? 0L : version + 1;
    }

    // ───────────── Nội bộ ─────────────

    private void emit(ChangeContext ctx, BrandChanged.Change change, String name, String oldName) {
        addDomainEvent(new BrandChanged(ctx.eventId(), ctx.now(), getId(), nextVersion(), change,
                ctx.actor().accountId(), name, oldName));
    }

    private static NameKey normalizedName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CatalogFieldException("name");
        }
        NameKey normalized = NameKey.of(raw);
        if (normalized.display().length() > NAME_MAX_LENGTH) {
            throw new CatalogFieldException("name");
        }
        return normalized;
    }

    // ───────────── Getters ─────────────

    public String getName()        { return name; }
    public String getNameKey()     { return nameKey; }
    public BrandStatus getStatus() { return status; }
    public Long version()          { return version; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }
}
