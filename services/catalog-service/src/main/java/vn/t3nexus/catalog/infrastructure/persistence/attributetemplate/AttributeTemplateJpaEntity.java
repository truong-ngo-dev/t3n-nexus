package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.time.Instant;

@Entity
@Table(name = "attribute_template")
@Getter
@Setter
@NoArgsConstructor
public class AttributeTemplateJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false, unique = true, updatable = false)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, updatable = false)
    private InputType inputType;

    @Column(name = "hint", length = 200)
    private String hint;

    @Column(name = "unit", length = 20, updatable = false)
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
