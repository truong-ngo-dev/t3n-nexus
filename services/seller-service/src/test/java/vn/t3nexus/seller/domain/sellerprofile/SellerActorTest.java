package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử SellerActor xử lý vai trò và tài khoản người dùng.
 */
class SellerActorTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void nullRoleNormalizesToEmptyString() {
        // role null thành chuỗi rỗng
        SellerActor actor = new SellerActor(ACCOUNT_ID, null);
        assertThat(actor.role()).isEmpty();
        assertThat(actor.isUser()).isFalse();
    }

    @Test
    void userRoleNormalizedCorrectly() {
        // " user " chuẩn hóa thành USER và isUser() đúng
        SellerActor actor = new SellerActor(ACCOUNT_ID, " user ");
        assertThat(actor.role()).isEqualTo("USER");
        assertThat(actor.isUser()).isTrue();
    }

    @Test
    void isUserReturnsFalseForNonRoles() {
        // ADMIN, rỗng, SELLER thì isUser() sai
        assertThat(new SellerActor(ACCOUNT_ID, "ADMIN").isUser()).isFalse();
        assertThat(new SellerActor(ACCOUNT_ID, "").isUser()).isFalse();
        assertThat(new SellerActor(ACCOUNT_ID, "SELLER").isUser()).isFalse();
    }

    @Test
    void requireUserPassesForUser() {
        // requireUser() không ném lỗi với USER
        SellerActor actor = new SellerActor(ACCOUNT_ID, "USER");
        actor.requireUser();
    }

    @Test
    void requireUserThrowsExceptionForAdmin() {
        // requireUser() ném DomainException mã SELLER_ROLE_NOT_ALLOWED với ADMIN
        SellerActor actor = new SellerActor(ACCOUNT_ID, "ADMIN");
        assertThatThrownBy(actor::requireUser)
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));
    }

    @Test
    void nullAccountIdThrowsNullPointerException() {
        // accountId null thì NullPointerException
        assertThatThrownBy(() -> new SellerActor(null, "USER"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isAdminReturnsTrueForAdminAndNormalizedAdmin() {
        // isAdmin() đúng với "ADMIN" và " admin " (đã chuẩn hóa), sai với "USER", rỗng, null
        assertThat(new SellerActor(ACCOUNT_ID, "ADMIN").isAdmin()).isTrue();
        assertThat(new SellerActor(ACCOUNT_ID, " admin ").isAdmin()).isTrue();
        assertThat(new SellerActor(ACCOUNT_ID, "USER").isAdmin()).isFalse();
        assertThat(new SellerActor(ACCOUNT_ID, "").isAdmin()).isFalse();
        assertThat(new SellerActor(ACCOUNT_ID, null).isAdmin()).isFalse();
    }

    @Test
    void requireAdminPassesForAdmin() {
        // requireAdmin() không ném lỗi với ADMIN
        SellerActor actor = new SellerActor(ACCOUNT_ID, "ADMIN");
        actor.requireAdmin();

        SellerActor normalizedActor = new SellerActor(ACCOUNT_ID, " admin ");
        normalizedActor.requireAdmin();
    }

    @Test
    void requireAdminThrowsExceptionForNonAdmin() {
        // requireAdmin() ném DomainException mã SELLER_ROLE_NOT_ALLOWED với USER, với vai rỗng và với vai null
        SellerActor userActor = new SellerActor(ACCOUNT_ID, "USER");
        assertThatThrownBy(userActor::requireAdmin)
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        SellerActor emptyRoleActor = new SellerActor(ACCOUNT_ID, "");
        assertThatThrownBy(emptyRoleActor::requireAdmin)
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        SellerActor nullRoleActor = new SellerActor(ACCOUNT_ID, null);
        assertThatThrownBy(nullRoleActor::requireAdmin)
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));
    }
}
