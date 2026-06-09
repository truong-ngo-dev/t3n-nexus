package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.utils.lang.Assert;

import java.time.Instant;

public class UserCredential extends AbstractAggregateRoot<UserId> implements AggregateRoot<UserId> {

    private final String               email;
    private       CredentialPassword   password;
    private final Role                 role;
    private final RegistrationMethod   registrationMethod;
    private       UserCredentialStatus status;
    private       boolean              mfaEnabled;
    private final Instant              createdAt;
    private       Instant              updatedAt;

    private UserCredential(UserId id, String email, CredentialPassword password,
                           Role role, RegistrationMethod registrationMethod, UserCredentialStatus status) {
        setId(id);
        this.email               = email;
        this.password            = password;
        this.role                = role;
        this.registrationMethod  = registrationMethod;
        this.status              = status;
        this.mfaEnabled          = false;
        this.createdAt           = Instant.now();
        this.updatedAt           = this.createdAt;
    }

    private UserCredential(UserId id, String email, CredentialPassword password,
                           Role role, RegistrationMethod registrationMethod, UserCredentialStatus status,
                           boolean mfaEnabled, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.email              = email;
        this.password           = password;
        this.role               = role;
        this.registrationMethod = registrationMethod;
        this.status             = status;
        this.mfaEnabled         = mfaEnabled;
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static UserCredential registerWithCredential(UserId id, String email, CredentialPassword password,
                                                        Role role, String fullName) {
        Assert.hasText(email, "email is required");
        Assert.notNull(password, "password is required");
        UserCredential credential = new UserCredential(id, email, password, role, RegistrationMethod.CREDENTIAL, UserCredentialStatus.PENDING);
        credential.addDomainEvent(new UserRegisteredEvent(id.getValue(), email, fullName, role.name(), RegistrationMethod.CREDENTIAL.name()));
        return credential;
    }

    public static UserCredential registerWithOAuth(UserId id, String email, Role role, String fullName, String setupToken) {
        Assert.hasText(email, "email is required");
        UserCredential credential = new UserCredential(id, email, null, role, RegistrationMethod.OAUTH, UserCredentialStatus.ACTIVE);
        credential.addDomainEvent(new UserRegisteredEvent(id.getValue(), email, fullName, role.name(), RegistrationMethod.OAUTH.name(), setupToken));
        return credential;
    }

    public static UserCredential reconstitute(UserId id, String email, CredentialPassword password,
                                              Role role, RegistrationMethod registrationMethod,
                                              UserCredentialStatus status, boolean mfaEnabled,
                                              Instant createdAt, Instant updatedAt) {
        return new UserCredential(id, email, password, role, registrationMethod, status, mfaEnabled, createdAt, updatedAt);
    }

    // ───────────── Status Transitions ─────────────

    public void activate() {
        if (!isPending()) throw UserCredentialException.invalidStatusTransition();
        this.status    = UserCredentialStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void lock() {
        if (isLocked()) return;
        this.status    = UserCredentialStatus.LOCKED;
        this.updatedAt = Instant.now();
    }

    public void unlock() {
        if (!isLocked()) throw UserCredentialException.invalidStatusTransition();
        this.status    = UserCredentialStatus.ACTIVE;
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

    public boolean verifyPassword(String rawPassword, PasswordService passwordService) {
        if (password == null) return false;
        return passwordService.verify(rawPassword, password.getHashedValue());
    }

    public boolean canLogin() {
        return isActive();
    }

    // ───────────── Queries ─────────────

    public boolean isPending() { return status == UserCredentialStatus.PENDING; }
    public boolean isActive()  { return status == UserCredentialStatus.ACTIVE; }
    public boolean isLocked()  { return status == UserCredentialStatus.LOCKED; }
    public boolean isOAuth()            { return registrationMethod == RegistrationMethod.OAUTH; }
    public boolean hasPassword()        { return password != null; }

    // ───────────── Password Management ─────────────

    public void setInitialPassword(CredentialPassword password) {
        if (!isOAuth()) throw UserCredentialException.notAllowedForCredentialUser();
        if (hasPassword()) throw UserCredentialException.passwordAlreadySet();
        this.password  = password;
        this.updatedAt = Instant.now();
    }

    public void changePassword(CredentialPassword newPassword) {
        if (!hasPassword()) throw UserCredentialException.noPasswordSet();
        this.password  = newPassword;
        this.updatedAt = Instant.now();
    }

    // ───────────── Getters ─────────────

    public String               getEmail()              { return email; }
    public CredentialPassword   getPassword()           { return password; }
    public Role                 getRole()               { return role; }
    public RegistrationMethod   getRegistrationMethod() { return registrationMethod; }
    public UserCredentialStatus getStatus()             { return status; }
    public boolean              isMfaEnabled()          { return mfaEnabled; }
    public Instant              getCreatedAt()          { return createdAt; }
    public Instant              getUpdatedAt()          { return updatedAt; }
}
