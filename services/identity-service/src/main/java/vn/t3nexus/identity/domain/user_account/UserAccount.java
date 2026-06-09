package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.utils.lang.Assert;

import java.time.Instant;

public class UserAccount extends AbstractAggregateRoot<UserId> implements AggregateRoot<UserId> {

    private final String email;
    private String phoneNumber;
    private String fullName;
    private String avatarUrl;
    private UserAccountStatus status;
    private Instant lockedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private UserAccount(UserId id, String email, String phoneNumber, String fullName, UserAccountStatus status) {
        setId(id);
        this.email       = email;
        this.phoneNumber = phoneNumber;
        this.fullName    = fullName;
        this.status      = status;
        this.createdAt   = Instant.now();
        this.updatedAt   = this.createdAt;
    }

    private UserAccount(UserId id, String email, String phoneNumber, String fullName, String avatarUrl,
                        UserAccountStatus status, Instant lockedAt, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.email       = email;
        this.phoneNumber = phoneNumber;
        this.fullName    = fullName;
        this.avatarUrl   = avatarUrl;
        this.status      = status;
        this.lockedAt    = lockedAt;
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static UserAccount reconstitute(UserId id, String email, String phoneNumber, String fullName,
                                           String avatarUrl, UserAccountStatus status, Instant lockedAt,
                                           Instant createdAt, Instant updatedAt) {
        return new UserAccount(id, email, phoneNumber, fullName, avatarUrl, status, lockedAt, createdAt, updatedAt);
    }

    public static UserAccount registerPendingVerification(UserId id, String email, String fullName) {
        Assert.notNull(email, "email is required");
        return new UserAccount(id, email, null, fullName, UserAccountStatus.PENDING);
    }

    public static UserAccount registerActivated(UserId id, String email, String fullName) {
        Assert.notNull(email, "email is required");
        return new UserAccount(id, email, null, fullName, UserAccountStatus.ACTIVE);
    }

    public static UserAccount registerCustomerPendingVerification(UserId id, String email, String fullName) {
        Assert.notNull(email, "email is required");
        UserAccount account = new UserAccount(id, email, null, fullName, UserAccountStatus.PENDING);
        account.addDomainEvent(new CustomerAccountCreatedEvent(id.getValue(), email, fullName));
        return account;
    }

    public static UserAccount registerCustomerActivated(UserId id, String email, String fullName, String setupToken) {
        Assert.notNull(email, "email is required");
        UserAccount account = new UserAccount(id, email, null, fullName, UserAccountStatus.ACTIVE);
        account.addDomainEvent(new CustomerAccountCreatedEvent(id.getValue(), email, fullName));
        account.addDomainEvent(new PasswordSetupEmailRequested(id.getValue(), email, fullName, setupToken));
        return account;
    }

    // ───────────── Profile ─────────────

    public void updateProfile(String fullName, String phoneNumber) {
        assertActive();
        if (fullName != null)    this.fullName    = fullName;
        if (phoneNumber != null) this.phoneNumber = phoneNumber;
        this.updatedAt = Instant.now();
    }

    public void updateAvatar(String avatarUrl) {
        assertActive();
        this.avatarUrl = avatarUrl;
        this.updatedAt = Instant.now();
    }

    // ───────────── Status ─────────────

    public void active() {
        if (!isPending()) throw UserAccountException.invalidStatusTransition();
        this.status    = UserAccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void lock() {
        if (isLocked()) return;
        this.status    = UserAccountStatus.LOCKED;
        this.lockedAt  = Instant.now();
        this.updatedAt = this.lockedAt;
    }

    public void unlock() {
        assertLocked();
        this.status    = UserAccountStatus.ACTIVE;
        this.lockedAt  = null;
        this.updatedAt = Instant.now();
    }

    // ───────────── Queries ─────────────

    public boolean isActive()  { return this.status == UserAccountStatus.ACTIVE; }
    public boolean isLocked()  { return this.status == UserAccountStatus.LOCKED; }
    public boolean isPending() { return this.status == UserAccountStatus.PENDING; }

    // ───────────── Guards ─────────────

    private void assertActive() {
        if (!isActive()) throw UserAccountException.userIsNotActive();
    }

    private void assertLocked() {
        if (!isLocked()) throw UserAccountException.userIsNotLocked();
    }

    // ───────────── Getters ─────────────

    public String getEmail()             { return email; }
    public String getPhoneNumber()       { return phoneNumber; }
    public String getFullName()          { return fullName; }
    public String getAvatarUrl()         { return avatarUrl; }
    public UserAccountStatus getStatus() { return status; }
    public Instant getLockedAt()         { return lockedAt; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
