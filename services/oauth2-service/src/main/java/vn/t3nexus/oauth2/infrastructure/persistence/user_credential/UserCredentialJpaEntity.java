package vn.t3nexus.oauth2.infrastructure.persistence.user_credential;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.oauth2.domain.user_credential.RegistrationMethod;
import vn.t3nexus.oauth2.domain.user_credential.Role;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialStatus;

import java.time.Instant;

@Entity
@Table(name = "user_credentials")
@Getter
@Setter
@NoArgsConstructor
public class UserCredentialJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_method", nullable = false)
    private RegistrationMethod registrationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserCredentialStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
