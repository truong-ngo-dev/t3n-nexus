package vn.t3nexus.oauth2.infrastructure.security.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;

@Getter
public class DeviceAwareWebAuthenticationDetails extends WebAuthenticationDetails {

    private final String deviceHash;     // từ hidden input — JS generated hash
    private final String userAgent;      // từ header
    private final String acceptLanguage; // từ header
    private final String ipAddress;      // extracted (X-Forwarded-For aware)

    public DeviceAwareWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.deviceHash     = request.getParameter("device_hash");
        this.userAgent      = request.getHeader("User-Agent");
        this.acceptLanguage = request.getHeader("Accept-Language");
        this.ipAddress      = IpAddressExtractor.extract(request);
    }

    @JsonCreator
    public DeviceAwareWebAuthenticationDetails(
            @JsonProperty("remoteAddress")  String remoteAddress,
            @JsonProperty("sessionId")      String sessionId,
            @JsonProperty("deviceHash")     String deviceHash,
            @JsonProperty("userAgent")      String userAgent,
            @JsonProperty("acceptLanguage") String acceptLanguage,
            @JsonProperty("ipAddress")      String ipAddress) {
        super(remoteAddress, sessionId);
        this.deviceHash     = deviceHash;
        this.userAgent      = userAgent;
        this.acceptLanguage = acceptLanguage;
        this.ipAddress      = ipAddress;
    }
}
