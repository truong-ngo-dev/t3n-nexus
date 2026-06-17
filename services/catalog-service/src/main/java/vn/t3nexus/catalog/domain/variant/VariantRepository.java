package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface VariantRepository extends Repository<Variant, VariantId> {

    boolean existsByOptionId(AttributeOptionId optionId);

    boolean existsByProductId(String productId);

    boolean existsActiveByProductId(String productId);

    boolean existsByProductIdAndCombinationHash(String productId, String combinationHash);

    List<Variant> findByProductId(String productId);
}
