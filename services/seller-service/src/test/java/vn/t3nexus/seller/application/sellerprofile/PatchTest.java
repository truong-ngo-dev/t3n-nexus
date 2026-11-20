package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.seller.application.sellerprofile.shared.Patch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử kiểu Patch mô tả trường cập nhật một phần.
 */
class PatchTest {

    @Test
    @DisplayName("Patch absent không present, isAbsent trả về true")
    void absentPatchProperties() {
        Patch<String> patch = Patch.absent();

        assertThat(patch.present()).isFalse();
        assertThat(patch.isAbsent()).isTrue();
    }

    @Test
    @DisplayName("Patch có giá trị thì present, value khớp")
    void presentPatchWithValue() {
        Patch<String> patch = Patch.of("x");

        assertThat(patch.present()).isTrue();
        assertThat(patch.isAbsent()).isFalse();
        assertThat(patch.value()).isEqualTo("x");
    }

    @Test
    @DisplayName("Patch chứa null biểu thị xóa trường tùy chọn")
    void presentPatchWithNullValue() {
        Patch<String> patch = Patch.of(null);

        assertThat(patch.present()).isTrue();
        assertThat(patch.isAbsent()).isFalse();
        assertThat(patch.value()).isNull();
    }

    @Test
    @DisplayName("resolve: vắng thì giữ giá trị hiện tại, có mặt thì lấy giá trị patch kể cả null")
    void resolveValue() {
        Patch<String> absent = Patch.absent();
        assertThat(absent.resolve("current")).isEqualTo("current");

        Patch<String> updated = Patch.of("new_value");
        assertThat(updated.resolve("current")).isEqualTo("new_value");

        Patch<String> cleared = Patch.of(null);
        assertThat(cleared.resolve("current")).isNull();
    }
}
