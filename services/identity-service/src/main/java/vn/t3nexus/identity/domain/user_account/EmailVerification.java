package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

public class EmailVerification extends AbstractAggregateRoot<EmailVerificationId> implements AggregateRoot<EmailVerificationId> {

    private static final int TOKEN_BYTES   = 32;
    private static final long EXPIRY_HOURS = 24;

    private final UserId userId;
    private final String email;
    private String token;
    private Instant expiresAt;
    private EmailVerificationStatus status;
    private Instant verifiedAt;
    private final Instant createdAt;

    private EmailVerification(EmailVerificationId id, UserId userId, String email,
                              String token, Instant expiresAt) {
        setId(id);
        this.userId    = userId;
        this.email     = email;
        this.token     = token;
        this.expiresAt = expiresAt;
        this.status    = EmailVerificationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    private EmailVerification(EmailVerificationId id, UserId userId, String email,
                              String token, Instant expiresAt,
                              EmailVerificationStatus status, Instant verifiedAt, Instant createdAt) {
        setId(id);
        this.userId     = userId;
        this.email      = email;
        this.token      = token;
        this.expiresAt  = expiresAt;
        this.status     = status;
        this.verifiedAt = verifiedAt;
        this.createdAt  = createdAt;
    }

    // ───────────── Factory Methods ─────────────

    public static EmailVerification issue(EmailVerificationId id, UserId userId, String email, String fullName) {
        byte[] raw = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(raw);
        String token      = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = Instant.now().plus(EXPIRY_HOURS, ChronoUnit.HOURS);
        EmailVerification ev = new EmailVerification(id, userId, email, token, expiresAt);
        ev.addDomainEvent(new VerificationEmailRequested(userId.getValue(), email, fullName, token));
        return ev;
    }

    public static EmailVerification reconstitute(EmailVerificationId id, UserId userId, String email,
                                                 String token, Instant expiresAt,
                                                 EmailVerificationStatus status, Instant verifiedAt,
                                                 Instant createdAt) {
        return new EmailVerification(id, userId, email, token, expiresAt, status, verifiedAt, createdAt);
    }

    // ───────────── Domain Methods ─────────────

    public void reissue(String fullName) {
        if (isVerified()) throw EmailVerificationException.alreadyVerified();
        byte[] raw = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(raw);
        this.token     = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        this.expiresAt = Instant.now().plus(EXPIRY_HOURS, ChronoUnit.HOURS);
        this.status    = EmailVerificationStatus.PENDING;
        addDomainEvent(new VerificationReissuedEvent(getId().getValue(), userId.getValue(), email, fullName, this.token));
    }

    public void verify(String fullName) {
        assertPending();
        if (isExpired()) throw EmailVerificationException.expired();
        this.status     = EmailVerificationStatus.VERIFIED;
        this.verifiedAt = Instant.now();
        addDomainEvent(new EmailVerifiedEvent(getId().getValue(), userId.getValue(), email, fullName));
    }

    // ───────────── Queries ─────────────

    public boolean isExpired()  { return Instant.now().isAfter(expiresAt); }
    public boolean isPending()  { return status == EmailVerificationStatus.PENDING; }
    public boolean isVerified() { return status == EmailVerificationStatus.VERIFIED; }

    // ───────────── Guards ─────────────

    private void assertPending() {
        if (isVerified()) throw EmailVerificationException.alreadyVerified();
        if (!isPending()) throw EmailVerificationException.invalid();
    }

    // ───────────── Getters ─────────────

    public UserId getUserId()              { return userId; }
    public String getEmail()                      { return email; }
    public String getToken()                      { return token; }
    public Instant getExpiresAt()                 { return expiresAt; }
    public EmailVerificationStatus getStatus()    { return status; }
    public Instant getVerifiedAt()                { return verifiedAt; }
    public Instant getCreatedAt()                 { return createdAt; }
}
