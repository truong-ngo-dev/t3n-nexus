package vn.t3nexus.identity.domain.device;

public interface UserAgentParser {

    record ParsedUserAgent(String browser, String os) {}

    ParsedUserAgent parse(String userAgent);
}
