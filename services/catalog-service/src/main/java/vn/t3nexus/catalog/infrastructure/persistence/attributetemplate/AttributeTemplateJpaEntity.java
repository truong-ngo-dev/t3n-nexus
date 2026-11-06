package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attribute_template")
@Getter
@Setter
@NoArgsConstructor
public class AttributeTemplateJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "hint")
    private String hint;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, updatable = false)
    private InputType inputType;

    @Column(name = "unit", updatable = false)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttributeTemplateStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
