package vn.t3nexus.catalog.infrastructure.persistence.category;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "category_closure")
@IdClass(CategoryClosureKey.class)
@Getter
@Setter
@NoArgsConstructor
public class CategoryClosureJpaEntity {

    @Id
    @Column(name = "ancestor_id", nullable = false, updatable = false)
    private String ancestorId;

    @Id
    @Column(name = "descendant_id", nullable = false, updatable = false)
    private String descendantId;

    @Column(name = "depth", nullable = false, updatable = false)
    private int depth;
}
