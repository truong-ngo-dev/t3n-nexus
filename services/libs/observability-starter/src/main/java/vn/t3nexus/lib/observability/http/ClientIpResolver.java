package vn.t3nexus.lib.observability.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Chọn địa chỉ mạng của người gọi để ghi vào {@code client.ip} (`tech.md` T7, feature 06). Dịch vụ đứng sau cổng chỉ thấy địa chỉ của cổng, nên
 * cổng đặt địa chỉ thật của người gọi vào tiêu đề {@code X-Client-Ip}. Tiêu đề này <b>chỉ được tin khi kết nối tới từ một proxy tin cậy</b>
 * ({@code observability.logging.http.trusted-proxies}: địa chỉ IP hoặc dải CIDR, mặc định loopback); từ nơi khác (gọi thẳng vào dịch vụ) thì bỏ qua,
 * vì ai cũng đặt được tiêu đề này. Giá trị phải là đúng một địa chỉ IP (không phân giải tên, không chữ lạ), nếu không bị bỏ để log không bị chèn nội dung.
 * Dùng chung cho bộ lọc servlet và WebFlux.
 */
public class ClientIpResolver {

    public static final String HEADER = "X-Client-Ip";
    public static final String UNKNOWN = "unknown";

    private static final Pattern IP_CHARS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private record Cidr(byte[] network, int prefixBits) {
        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) return false;
            }
            int remaining = prefixBits % 8;
            if (remaining == 0) return true;
            int mask = 0xFF << (8 - remaining);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    private final List<Cidr> trusted = new ArrayList<>();

    public ClientIpResolver(List<String> trustedProxies) {
        for (String entry : trustedProxies) {
            String text = entry.strip();
            if (text.isEmpty()) continue;
            int slash = text.indexOf('/');
            byte[] network = parseLiteral(slash < 0 ? text : text.substring(0, slash));
            if (network == null) throw new IllegalArgumentException("trusted-proxies: không phải địa chỉ IP hay dải CIDR: " + entry);
            int bits = slash < 0 ? network.length * 8 : Integer.parseInt(text.substring(slash + 1));
            trusted.add(new Cidr(network, bits));
        }
    }

    /** @return giá trị của tiêu đề nếu kết nối tới từ proxy tin cậy và giá trị là một địa chỉ IP; ngược lại địa chỉ kết nối */
    public String resolve(String remoteAddress, String headerValue) {
        if (remoteAddress == null) return UNKNOWN;
        if (headerValue == null || headerValue.isBlank()) return remoteAddress;
        String candidate = headerValue.strip();
        if (!isTrusted(remoteAddress) || parseLiteral(candidate) == null) return remoteAddress;
        return candidate;
    }

    private boolean isTrusted(String remoteAddress) {
        byte[] address = parseLiteral(remoteAddress.strip());
        if (address == null) return false;
        for (Cidr cidr : trusted) {
            if (cidr.contains(address)) return true;
        }
        return false;
    }

    /** Byte của địa chỉ nếu chuỗi đúng là một địa chỉ IP dạng chữ số; {@code null} nếu không (không bao giờ phân giải tên máy). */
    private static byte[] parseLiteral(String text) {
        if (!IP_CHARS.matcher(text).matches()) return null;
        try {
            return InetAddress.getByName(text).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
