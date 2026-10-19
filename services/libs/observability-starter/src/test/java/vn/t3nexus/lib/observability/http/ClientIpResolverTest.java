package vn.t3nexus.lib.observability.http;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-24 (feature 06, `tech.md` T7, R19): {@code client.ip} chỉ lấy từ tiêu đề {@code X-Client-Ip} khi kết nối tới từ một proxy tin cậy
 * (cổng của ta); từ nơi khác thì tiêu đề bị bỏ qua, vì client gọi thẳng có thể tự đặt tiêu đề này. Giá trị không phải địa chỉ IP cũng bị bỏ
 * (tránh chèn nội dung vào log).
 */
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(List.of("127.0.0.1", "::1", "10.1.0.0/16"));

    @Test
    void the_header_is_used_when_the_connection_comes_from_a_trusted_proxy() {
        assertThat(resolver.resolve("127.0.0.1", "203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve("0:0:0:0:0:0:0:1", "203.0.113.9")).as("loopback IPv6 viết dài").isEqualTo("203.0.113.9");
        assertThat(resolver.resolve("10.1.200.3", "2001:db8::7")).as("trong dải CIDR, địa chỉ IPv6 hợp lệ").isEqualTo("2001:db8::7");
    }

    @Test
    void the_header_is_ignored_when_the_connection_is_not_from_a_trusted_proxy() {
        assertThat(resolver.resolve("198.51.100.4", "203.0.113.9")).isEqualTo("198.51.100.4");
        assertThat(resolver.resolve("10.2.0.1", "203.0.113.9")).as("ngoài dải CIDR").isEqualTo("10.2.0.1");
    }

    @Test
    void a_missing_blank_or_malformed_header_falls_back_to_the_connection_address() {
        assertThat(resolver.resolve("127.0.0.1", null)).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve("127.0.0.1", "  ")).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve("127.0.0.1", "evil\nINJECT")).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve("127.0.0.1", "1.2.3.4, 5.6.7.8")).as("không phải một địa chỉ").isEqualTo("127.0.0.1");
        assertThat(resolver.resolve("127.0.0.1", "not-an-ip")).isEqualTo("127.0.0.1");
    }

    @Test
    void a_null_connection_address_gives_unknown_instead_of_failing() {
        assertThat(resolver.resolve(null, "203.0.113.9")).isEqualTo("unknown");
    }
}
