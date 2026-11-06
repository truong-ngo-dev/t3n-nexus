package vn.t3nexus.catalog.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-02: hàm khóa tên (INV-CAT-02, 10, 11, 20). */
class NameKeyTest {

    @ParameterizedTest
    @CsvSource({
            "Áo Thun,ao thun",
            "ĐIỆN THOẠI,dien thoai",
            "đồ chơi,do choi",
            "Xuất xứ,xuat xu",
            "SAMSUNG,samsung",
    })
    void keyIsLowercaseWithoutDiacritics(String raw, String expectedKey) {
        assertThat(NameKey.of(raw).key()).isEqualTo(expectedKey);
    }

    @Test
    void trimsAndCollapsesWhitespaceInBothDisplayAndKey() {
        NameKey name = NameKey.of("  Áo \t  thun\n nam  ");

        assertThat(name.display()).isEqualTo("Áo thun nam");
        assertThat(name.key()).isEqualTo("ao thun nam");
    }

    @Test
    void displayKeepsCaseAndDiacritics() {
        assertThat(NameKey.of("Điện Thoại").display()).isEqualTo("Điện Thoại");
    }

    @Test
    void namesDifferingOnlyByDiacriticsOrCaseOrSpacesShareKey() {
        String key = NameKey.of("Màu sắc").key();

        assertThat(NameKey.of("Mau sac").key()).isEqualTo(key);
        assertThat(NameKey.of("MÀU   SẮC").key()).isEqualTo(key);
        assertThat(NameKey.of(" mau sắc ").key()).isEqualTo(key);
    }

    @Test
    void composedAndDecomposedUnicodeShareKey() {
        String composed = Normalizer.normalize("Điện thoại", Normalizer.Form.NFC);
        String decomposed = Normalizer.normalize("Điện thoại", Normalizer.Form.NFD);

        assertThat(NameKey.of(decomposed).key()).isEqualTo(NameKey.of(composed).key());
    }

    @Test
    void differentWordsHaveDifferentKeys() {
        assertThat(NameKey.of("Áo thun").key()).isNotEqualTo(NameKey.of("Áo thun nam").key());
        assertThat(NameKey.of("Ao thun").key()).isNotEqualTo(NameKey.of("Aothun").key());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlank(String raw) {
        assertThatThrownBy(() -> NameKey.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> NameKey.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
