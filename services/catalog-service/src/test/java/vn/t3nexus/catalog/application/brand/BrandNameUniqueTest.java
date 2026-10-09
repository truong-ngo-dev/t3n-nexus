package vn.t3nexus.catalog.application.brand;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** INV-CAT-021 — tên thương hiệu duy nhất toàn sàn, kiểm cả lúc tạo lẫn lúc đổi tên. */
class BrandNameUniqueTest {

    private static final BrandId ID = BrandId.of("01BRAND0000000000000000000");

    private final BrandRepository repository = mock(BrandRepository.class);
    private final ULIDGenerator ulidGenerator = mock(ULIDGenerator.class);

    @Test
    void createRejectsExistingName() {
        when(repository.existsByName("Apple")).thenReturn(true);

        assertThatThrownBy(() -> new CreateBrand(repository, ulidGenerator)
                .handle(new CreateBrand.Command("Apple", "apple")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(BrandErrorCode.BRAND_NAME_ALREADY_EXISTS);
        verify(repository, never()).save(any(Brand.class));
    }

    @Test
    void createRejectsExistingSlug() {
        when(repository.existsBySlug("apple")).thenReturn(true);

        assertThatThrownBy(() -> new CreateBrand(repository, ulidGenerator)
                .handle(new CreateBrand.Command("Apple", "apple")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(BrandErrorCode.BRAND_SLUG_ALREADY_EXISTS);
    }

    @Test
    void renameRejectsNameOfAnotherBrand() {
        when(repository.findById(ID)).thenReturn(Optional.of(Brand.create(ID, "Aple", "apple")));
        when(repository.existsByNameExcludingId("Samsung", ID)).thenReturn(true);

        assertThatThrownBy(() -> new UpdateBrand(repository).handle(new UpdateBrand.Command(ID.getValue(), "Samsung")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(BrandErrorCode.BRAND_NAME_ALREADY_EXISTS);
        verify(repository, never()).save(any(Brand.class));
    }
}
