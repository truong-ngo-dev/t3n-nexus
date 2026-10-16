package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHasher;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookie;

/**
 * {@code details} của yêu cầu đăng nhập bằng mật khẩu: địa chỉ mạng, trình duyệt và <b>bản băm</b> của mã thiết bị (cookie
 * {@code DEVICE_KEY}). Bản băm được tính ngay ở nơi dựng đối tượng, nên mã thô không bao giờ vào {@code Authentication}.
 *
 * <p>Giữ đúng hành vi của Spring: {@code ProviderManager} chép {@code details} của yêu cầu sang token đã xác thực, rồi token
 * được lưu vào session (JDK serialization) và vào bản ghi ủy quyền (JSON, cần mixin ở cấu hình dịch vụ ủy quyền). Vì vậy lớp này
 * kế thừa {@link WebAuthenticationDetails} và chỉ mang giá trị đã an toàn để lưu (ADR-0003 mục 4, ADR-0005). Lớp này thay cho
 * lớp {@code details} của mô hình cũ, nơi mã thiết bị là vân tay do trình duyệt tự khai.
 */
public class LoginRequestDetails extends WebAuthenticationDetails {

    private static final long serialVersionUID = 1L;

    private final String ipAddress;
    private final String userAgent;
    private final String deviceKeyHash;     // null khi yêu cầu chưa có cookie mã thiết bị

    public LoginRequestDetails(HttpServletRequest request, DeviceKeyHasher deviceKeyHasher) {
        super(request);
        this.ipAddress     = IpAddressExtractor.extract(request);
        this.userAgent     = request.getHeader("User-Agent");
        this.deviceKeyHash = DeviceKeyCookie.read(request).map(raw -> deviceKeyHasher.hash(raw).value()).orElse(null);
    }

    @JsonCreator
    public LoginRequestDetails(@JsonProperty("remoteAddress") String remoteAddress,
                               @JsonProperty("sessionId") String sessionId,
                               @JsonProperty("ipAddress") String ipAddress,
                               @JsonProperty("userAgent") String userAgent,
                               @JsonProperty("deviceKeyHash") String deviceKeyHash) {
        super(remoteAddress, sessionId);
        this.ipAddress     = ipAddress;
        this.userAgent     = userAgent;
        this.deviceKeyHash = deviceKeyHash;
    }

    public String getIpAddress()     { return ipAddress; }
    public String getUserAgent()     { return userAgent; }
    public String getDeviceKeyHash() { return deviceKeyHash; }
}
