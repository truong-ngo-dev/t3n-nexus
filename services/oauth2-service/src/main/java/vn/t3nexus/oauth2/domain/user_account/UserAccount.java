package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.utils.lang.Assert;

import java.time.Instant;

public class UserAccount extends AbstractAggregateRoot<UserAccountId> implements AggregateRoot<UserAccountId> {

    private final Email                email;
    private       PasswordHash         passwordHash;
    private final Role                 role;
    private final RegistrationMethod   registrationMethod;
    private       UserAccountStatus    status;
    private       boolean              mfaEnabled;
    private final Instant              createdAt;
    private       Instant              updatedAt;

    private UserAccount(UserAccountId id, Email email, PasswordHash passwordHash,
                        Role role, RegistrationMethod registrationMethod, UserAccountStatus status, Instant now) {
        setId(id);
        this.email               = email;
        this.passwordHash = passwordHash;
        this.role                = role;
        this.registrationMethod  = registrationMethod;
        this.status              = status;
        this.mfaEnabled          = false;
        this.createdAt           = now;
        this.updatedAt           = now;
    }

    private UserAccount(UserAccountId id, Email email, PasswordHash passwordHash,
                        Role role, RegistrationMethod registrationMethod, UserAccountStatus status,
                        boolean mfaEnabled, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.email              = email;
        this.passwordHash = passwordHash;
        this.role               = role;
        this.registrationMethod = registrationMethod;
        this.status             = status;
        this.mfaEnabled         = mfaEnabled;
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    /**
     * Đăng ký công khai. Mã, giá trị băm và thời điểm do use case tính trước rồi truyền vào (aggregate không giữ
     * cổng nào). Vai luôn là {@link Role#USER} (INV-ATH-06); trạng thái {@link UserAccountStatus#ACTIVE}.
     * Điều kiện "email chưa dùng, không thuộc miền nội bộ" do {@link UserAccountService} kiểm trước.
     */
    public static UserAccount register(UserAccountId id, Email email, PasswordHash passwordHash, Instant now) {
        Assert.notNull(id, "id is required");
        Assert.notNull(email, "email is required");
        Assert.notNull(passwordHash, "passwordHash is required");
        Assert.notNull(now, "now is required");
        UserAccount account = new UserAccount(id, email, passwordHash, Role.USER,
                RegistrationMethod.CREDENTIAL, UserAccountStatus.ACTIVE, now);
        account.addDomainEvent(new UserAccountRegistered(id, email, Role.USER, now));
        return account;
    }

    public static UserAccount registerWithOAuth(UserAccountId id, Email email, Role role, String fullName, String setupToken) {
        Assert.notNull(email, "email is required");
        UserAccount account = new UserAccount(id, email, null, role, RegistrationMethod.OAUTH,
                UserAccountStatus.ACTIVE, Instant.now());
        account.addDomainEvent(new UserRegisteredEvent(id.getValueAsString(), email.value(), fullName, role.name(),
                RegistrationMethod.OAUTH.name(), setupToken));
        return account;
    }

    public static UserAccount reconstitute(UserAccountId id, Email email, PasswordHash passwordHash,
                                           Role role, RegistrationMethod registrationMethod,
                                           UserAccountStatus status, boolean mfaEnabled,
                                           Instant createdAt, Instant updatedAt) {
        return new UserAccount(id, email, passwordHash, role, registrationMethod, status, mfaEnabled, createdAt, updatedAt);
    }

    // ───────────── Status Transitions ─────────────

    public void activate() {
        if (!isPending()) throw UserAccountException.invalidStatusTransition();
        this.status    = UserAccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void lock() {
        if (isLocked()) return;
        this.status    = UserAccountStatus.LOCKED;
        this.updatedAt = Instant.now();
    }

    public void unlock() {
        if (!isLocked()) throw UserAccountException.invalidStatusTransition();
        this.status    = UserAccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    // ───────────── MFA ─────────────

    public void enableMfa() {
        this.mfaEnabled = true;
        this.updatedAt  = Instant.now();
    }

    public void disableMfa() {
        this.mfaEnabled = false;
        this.updatedAt  = Instant.now();
    }

    // ───────────── Login ─────────────

    /** Nhận bộ băm qua tham số vì cần chính {@code passwordHash} của aggregate (double dispatch). */
    public boolean verifyPassword(String attempt, PasswordHasher passwordHasher) {
        if (passwordHash == null) return false;
        return passwordHasher.verify(attempt, passwordHash);
    }

    public boolean canLogin() {
        return isActive();
    }

    // ───────────── Queries ─────────────

    public boolean isPending() { return status == UserAccountStatus.PENDING; }
    public boolean isActive()  { return status == UserAccountStatus.ACTIVE; }
    public boolean isLocked()  { return status == UserAccountStatus.LOCKED; }
    public boolean isOAuth()            { return registrationMethod == RegistrationMethod.OAUTH; }
    public boolean hasPassword()        { return passwordHash != null; }

    // ───────────── Password Management ─────────────

    public void setInitialPassword(PasswordHash passwordHash) {
        if (!isOAuth()) throw UserAccountException.notAllowedForCredentialUser();
        if (hasPassword()) throw UserAccountException.passwordAlreadySet();
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public void changePassword(PasswordHash newPassword) {
        if (!hasPassword()) throw UserAccountException.noPasswordSet();
        this.passwordHash = newPassword;
        this.updatedAt = Instant.now();
    }

    // ───────────── Getters ─────────────

    public Email               getEmail()              { return email; }
    public PasswordHash        getPasswordHash()       { return passwordHash; }
    public Role                getRole()               { return role; }
    public RegistrationMethod  getRegistrationMethod() { return registrationMethod; }
    public UserAccountStatus   getStatus()             { return status; }
    public boolean             isMfaEnabled()          { return mfaEnabled; }
    public Instant             getCreatedAt()          { return createdAt; }
    public Instant             getUpdatedAt()          { return updatedAt; }
}
