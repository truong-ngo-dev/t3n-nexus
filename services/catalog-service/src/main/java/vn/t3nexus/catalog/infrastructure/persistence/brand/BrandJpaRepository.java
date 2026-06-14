package vn.t3nexus.catalog.infrastructure.persistence.brand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;

import java.util.List;

@Repository
public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, String> {

    boolean existsBySlug(String slug);

    List<BrandJpaEntity> findAllByStatus(BrandStatus status);
}
