package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attribute_option")
@Getter
@Setter
@NoArgsConstructor
public class AttributeOptionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "value", nullable = false, updatable = false)
    private String value;

    @Column(name = "display_value", nullable = false)
    private String displayValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttributeOptionStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
