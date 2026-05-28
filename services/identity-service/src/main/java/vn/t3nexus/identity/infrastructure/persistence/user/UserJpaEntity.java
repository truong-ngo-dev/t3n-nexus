package vn.t3nexus.identity.infrastructure.persistence.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.identity.domain.user.Role;
import vn.t3nexus.identity.domain.user.UserStatus;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "hashed_password")
    private String hashedPassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

//    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
//    @JoinColumn(name = "user_id")
//    private Set<SocialConnectionJpaEntity> socialConnections = new HashSet<>();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
