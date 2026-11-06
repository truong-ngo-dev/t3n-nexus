package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị Contact (thông tin liên hệ).
 */
class ContactTest {

    @Test
    void validWithAllThreeFields() {
        // hợp lệ đủ ba trường
        Contact contact = Contact.of("Nguyễn Văn A", "0912345678", "Số 1 Đại Cồ Việt, Hà Nội");
        assertThat(contact.personName()).isEqualTo("Nguyễn Văn A");
        assertThat(contact.phone().value()).isEqualTo("0912345678");
        assertThat(contact.address()).isNotNull();
        assertThat(contact.address().value()).isEqualTo("Số 1 Đại Cồ Việt, Hà Nội");
    }

    @Test
    void nullOrBlankAddressYieldsNullAddress() {
        // địa chỉ null hoặc toàn khoảng trắng thì address() là null
        Contact c1 = Contact.of("Nguyễn Văn A", "0912345678", null);
        assertThat(c1.address()).isNull();

        Contact c2 = Contact.of("Nguyễn Văn A", "0912345678", "    ");
        assertThat(c2.address()).isNull();
    }

    @Test
    void addressNormalizedWhenPresent() {
        // có địa chỉ thì chuẩn hóa
        Contact contact = Contact.of("Nguyễn Văn A", "0912345678", "  Số   1  Đại Cồ Việt,   Hà Nội  ");
        assertThat(contact.address()).isNotNull();
        assertThat(contact.address().value()).isEqualTo("Số 1 Đại Cồ Việt, Hà Nội");
    }

    @Test
    void personNameValidation() {
        // tên 1 ký tự lỗi (contact.personName)
        String input1 = "A";
        assertThatThrownBy(() -> Contact.of(input1, "0912345678", null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.personName");
                    assertThat(fe.getMessage()).doesNotContain("12345678901x");
                });

        // 2 được
        Contact c2 = Contact.of("An", "0912345678", null);
        assertThat(c2.personName()).isEqualTo("An");

        // 100 được
        String str100 = "a".repeat(100);
        Contact c100 = Contact.of(str100, "0912345678", null);
        assertThat(c100.personName()).isEqualTo(str100);

        // 101 lỗi
        String input101 = "12345678901x" + "a".repeat(89);
        assertThatThrownBy(() -> Contact.of(input101, "0912345678", null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.personName");
                    assertThat(fe.getMessage()).doesNotContain(input101);
                });

        // null lỗi
        assertThatThrownBy(() -> Contact.of(null, "0912345678", null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.personName");
                });
    }

    @Test
    void invalidPhoneThrowsContactPhoneException() {
        // số điện thoại sai lỗi contact.phone
        String badPhone = "12345678901x";
        assertThatThrownBy(() -> Contact.of("Nguyễn Văn A", badPhone, null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.phone");
                    assertThat(fe.getMessage()).doesNotContain(badPhone);
                });
    }

    @Test
    void addressTooShortThrowsContactAddressException() {
        // địa chỉ có nhưng quá ngắn lỗi contact.address
        String shortAddr = "123456789";
        assertThatThrownBy(() -> Contact.of("Nguyễn Văn A", "0912345678", shortAddr))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.address");
                    assertThat(fe.getMessage()).doesNotContain(shortAddr);
                });
    }

    @Test
    void equalsAndHashCodeBasedOnAllThreeFields() {
        // equals theo cả ba trường
        Contact c1 = Contact.of("Nguyễn Văn A", "0912345678", "Số 1 Đại Cồ Việt, Hà Nội");
        Contact c2 = Contact.of("Nguyễn Văn A", "+84912345678", "Số 1 Đại Cồ Việt, Hà Nội");
        Contact c3 = Contact.of("Nguyễn Văn A", "0912345678", null);

        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        assertThat(c1).isNotEqualTo(c3);
    }

    @Test
    void maskSensoryDataInToString() {
        // toString() bằng Contact[***]
        Contact contact = Contact.of("Nguyễn Văn A", "0912345678", "Số 1 Đại Cồ Việt, Hà Nội");
        assertThat(contact.toString()).isEqualTo("Contact[***]");
        assertThat(contact.toString()).doesNotContain("Nguyễn Văn A");
        assertThat(contact.toString()).doesNotContain("0912345678");
    }
}
