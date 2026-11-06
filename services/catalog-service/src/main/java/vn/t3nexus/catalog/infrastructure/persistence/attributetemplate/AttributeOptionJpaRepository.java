package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttributeOptionJpaRepository extends JpaRepository<AttributeOptionJpaEntity, UUID> {

    List<AttributeOptionJpaEntity> findByTemplateId(UUID templateId);

    /** Theo lô: chỉ nạp tùy chọn của đúng các mẫu cần. */
    List<AttributeOptionJpaEntity> findByTemplateIdIn(Collection<UUID> templateIds);
}
