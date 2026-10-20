package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-22 (feature 05, R15): sau khi callback đăng nhập thành công, BFF chuyển trình duyệt về trang tài khoản của
 * ứng dụng Cửa hàng ở host {@code shop}, không về địa chỉ cũ. Các test khác chỉ kiểm mã 302, không kiểm địa chỉ.
 */
class LoginRedirectTest extends GatewayTestBase {

    @Test
    void successful_login_redirects_to_the_account_page_at_the_shop_host() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");

        assertThat(login.status()).isEqualTo(302);
        assertThat(login.location()).isEqualTo("http://shop.localhost:8000/account");
    }

    @Test
    void the_old_customer_path_is_not_used() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");

        assertThat(login.location()).doesNotContain("/customer");
    }
}
