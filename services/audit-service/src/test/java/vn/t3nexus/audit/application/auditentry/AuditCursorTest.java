package vn.t3nexus.audit.application.auditentry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.t3nexus.audit.domain.auditentry.AuditErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditCursorTest {

    @Test
    void encodeThenDecodePreservesValuesIncludingTruncationToMicros() {
        // Nano giây 123456789 sẽ được cắt về micro giây 123456000 khi decode
        Instant original = Instant.parse("2026-10-25T14:30:00.123456789Z");
        UUID id = UUID.randomUUID();

        AuditCursor cursor = new AuditCursor(original, id);
        String encoded = cursor.encode();
        AuditCursor decoded = AuditCursor.decode(encoded);

        Instant expectedInstant = Instant.parse("2026-10-25T14:30:00.123456Z");
        assertThat(decoded.occurredAt()).isEqualTo(expectedInstant);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void encodeThenDecodeWorksForInstantBeforeEpoch1970() {
        // Thời điểm trước 1970 (epoch second âm)
        Instant beforeEpoch = Instant.parse("1968-05-12T08:15:30.987654Z");
        UUID id = UUID.randomUUID();

        AuditCursor cursor = new AuditCursor(beforeEpoch, id);
        String encoded = cursor.encode();
        AuditCursor decoded = AuditCursor.decode(encoded);

        assertThat(decoded.occurredAt()).isEqualTo(beforeEpoch);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void encodedStringIsBase64UrlWithoutPadding() {
        AuditCursor cursor = new AuditCursor(Instant.parse("2026-10-25T00:00:00Z"), UUID.randomUUID());
        String encoded = cursor.encode();

        // Không chứa '=', '+', '/' theo chuẩn base64url không đệm
        assertThat(encoded)
                .doesNotContain("=")
                .doesNotContain("+")
                .doesNotContain("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "!!!",
            "not-base64@#$",
            "bm90LWEtdmFsaWQtY3Vyc29y", // Base64 của "not-a-valid-cursor" (không có dấu '.')
            "YWJjLnh5eg",             // Base64 của "abc.xyz" (micros không phải số)
            "MTIzNDU2LmludmFsaWQtdXVpZA", // Base64 của "123456.invalid-uuid" (uuid sai)
            "MTIzNDU2Lg",             // Base64 của "123456." (thiếu uuid)
            "LnV1aWQ"                 // Base64 của ".uuid" (thiếu micros)
    })
    void invalidInputsThrowDomainExceptionWithCursorInvalidCode(String invalidInput) {
        assertThatThrownBy(() -> AuditCursor.decode(invalidInput))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_CURSOR_INVALID));
    }

    @Test
    void nullInputThrowsDomainExceptionWithCursorInvalidCode() {
        assertThatThrownBy(() -> AuditCursor.decode(null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_CURSOR_INVALID));
    }
}
