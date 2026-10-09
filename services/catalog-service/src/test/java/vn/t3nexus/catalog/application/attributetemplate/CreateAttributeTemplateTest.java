package vn.t3nexus.catalog.application.attributetemplate;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** INV-CAT-011 — mã định danh thuộc tính duy nhất toàn sàn (kể cả thuộc tính đã ngừng dùng). */
class CreateAttributeTemplateTest {

    private final AttributeTemplateRepository repository = mock(AttributeTemplateRepository.class);
    private final ULIDGenerator ulidGenerator = mock(ULIDGenerator.class);
    private final CreateAttributeTemplate handler = new CreateAttributeTemplate(repository, ulidGenerator);

    @Test
    void rejectsExistingName() {
        when(repository.existsByName("color")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(
                new CreateAttributeTemplate.Command("color", "Màu sắc", null, InputType.SINGLE_SELECT, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(AttributeTemplateErrorCode.TEMPLATE_NAME_EXISTS);
        verify(repository, never()).save(any(AttributeTemplate.class));
    }

    @Test
    void savesNewName() {
        when(repository.existsByName("color")).thenReturn(false);
        when(ulidGenerator.generate()).thenReturn("01TEMPLATE00000000000000000");

        handler.handle(new CreateAttributeTemplate.Command("color", "Màu sắc", null, InputType.SINGLE_SELECT, null));

        verify(repository).save(any(AttributeTemplate.class));
    }
}
