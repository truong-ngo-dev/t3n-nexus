package vn.t3nexus.identity.domain.user;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.utils.lang.Assert;

import java.time.Instant;

public class User extends AbstractAggregateRoot<UserId> implements AggregateRoot<UserId> {

    private final String email;
    private String phoneNumber;
    private String fullName;
    private UserPassword hashedPassword;
    private UserStatus status;
    private final Role role;
    private Instant lockedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, String email, String phoneNumber, String fullName,
                 UserPassword password, UserStatus status, Role role) {
        setId(id);
        this.email          = email;
        this.phoneNumber    = phoneNumber;
        this.fullName       = fullName;
        this.hashedPassword = password;
        this.status         = status;
        this.role           = role;
        this.createdAt      = Instant.now();
        this.updatedAt      = this.createdAt;
    }

    private User(UserId id, String email, String phoneNumber, String fullName,
                 UserPassword password, UserStatus status, Role role,
                 Instant lockedAt, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.email          = email;
        this.phoneNumber    = phoneNumber;
        this.fullName       = fullName;
        this.hashedPassword = password;
        this.status         = status;
        this.role           = role;
        this.lockedAt       = lockedAt;
        this.createdAt      = createdAt;
        this.updatedAt      = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static User reconstitute(UserId id, String email, String phoneNumber, String fullName,
                                    UserPassword password, UserStatus status, Role role,
                                    Instant lockedAt, Instant createdAt, Instant updatedAt) {
        return new User(id, email, phoneNumber, fullName, password, status, role, lockedAt, createdAt, updatedAt);
    }

    public static User registerAsCustomer(UserId id, String email, String fullName, UserPassword password, String verificationToken) {
        Assert.notNull(email, "email is required");
        User user = new User(id, email, null, fullName, password, UserStatus.PENDING, Role.CUSTOMER);
        user.addDomainEvent(new CustomerRegisteredEvent(
                id.getValue(), email, fullName, CustomerRegisteredEvent.RegistrationMethod.CREDENTIAL, verificationToken));
        return user;
    }

    public static User registerAsCustomerViaOAuth(UserId id, String email, String fullName) {
        Assert.notNull(email, "email is required");
        User user = new User(id, email, null, fullName, null, UserStatus.ACTIVE, Role.CUSTOMER);
        user.addDomainEvent(new CustomerRegisteredEvent(
                id.getValue(), email, fullName, CustomerRegisteredEvent.RegistrationMethod.OAUTH, null));
        return user;
    }

    public static User applyAsSeller(UserId id, String email, String fullName, UserPassword password) {
        Assert.notNull(email, "email is required");
        User user = new User(id, email, null, fullName, password, UserStatus.PENDING, Role.SELLER);
//        user.addDomainEvent(new SellerAppliedEvent(id.getValue(), email, fullName));
        return user;
    }

    public static User createShipper(UserId id, String email, String fullName, UserPassword password) {
        Assert.notNull(email, "email is required");
        User user = new User(id, email, null, fullName, password, UserStatus.ACTIVE, Role.SHIPPER);
//        user.addDomainEvent(new ShipperCreatedEvent(id.getValue(), email, fullName));
        return user;
    }

    public static User createAdmin(UserId id, String email, String fullName, UserPassword password) {
        Assert.notNull(email, "email is required");
        return new User(id, email, null, fullName, password, UserStatus.ACTIVE, Role.ADMIN);
    }

    // ───────────── Profile ─────────────

    public void updateProfile(String fullName, String phoneNumber) {
        assertActive();
        if (fullName != null)    this.fullName    = fullName;
        if (phoneNumber != null) this.phoneNumber = phoneNumber;
        this.updatedAt = Instant.now();
    }

    // ───────────── Password ─────────────

    public void changePassword(UserPassword newPassword) {
        assertActive();
        Assert.notNull(newPassword, "newPassword is required");
        this.hashedPassword = newPassword;
        this.updatedAt = Instant.now();
    }

    // ───────────── Status ─────────────

    public void active() {
        if (!isPending()) throw UserException.invalidStatusTransition();
        this.status    = UserStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void lock() {
        if (isLocked()) return;
        this.status    = UserStatus.LOCKED;
        this.lockedAt  = Instant.now();
        this.updatedAt = this.lockedAt;
    }

    public void unlock() {
        assertLocked();
        this.status    = UserStatus.ACTIVE;
        this.lockedAt  = null;
        this.updatedAt = Instant.now();
    }

    // ───────────── Queries ─────────────

    public boolean isActive()    { return this.status == UserStatus.ACTIVE; }
    public boolean isLocked()    { return this.status == UserStatus.LOCKED; }
    public boolean isPending()   { return this.status == UserStatus.PENDING; }
    public boolean hasPassword() { return this.hashedPassword != null; }

    // ───────────── Guards ─────────────

    private void assertActive() {
        if (!isActive()) throw UserException.userIsNotActive();
    }

    private void assertLocked() {
        if (!isLocked()) throw UserException.userIsNotLocked();
    }

    // ───────────── Getters ─────────────

    public String getEmail()                  { return email; }
    public String getPhoneNumber()            { return phoneNumber; }
    public String getFullName()               { return fullName; }
    public UserPassword getHashedPassword()   { return hashedPassword; }
    public UserStatus getStatus()             { return status; }
    public Role getRole()                     { return role; }
    public Instant getLockedAt()              { return lockedAt; }
    public Instant getCreatedAt()             { return createdAt; }
    public Instant getUpdatedAt()             { return updatedAt; }
}
