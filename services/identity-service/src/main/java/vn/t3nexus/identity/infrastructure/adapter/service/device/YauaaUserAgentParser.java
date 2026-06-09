package vn.t3nexus.identity.infrastructure.adapter.service.device;

import lombok.RequiredArgsConstructor;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.device.UserAgentParser;

@Component
@RequiredArgsConstructor
public class YauaaUserAgentParser implements UserAgentParser {

    private final UserAgentAnalyzer analyzer;

    @Override
    public ParsedUserAgent parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ParsedUserAgent("Unknown", "Unknown");
        }

        UserAgent agent   = analyzer.parse(userAgent);
        String    browser = agent.getValue("AgentNameVersion");
        String    os      = agent.getValue("OperatingSystemName");

        return new ParsedUserAgent(
                isUnknown(browser) ? "Unknown" : browser,
                isUnknown(os)      ? "Unknown" : os
        );
    }

    private boolean isUnknown(String value) {
        return value == null        ||
               value.isBlank()     ||
               "??".equals(value)  ||
               "Unknown".equalsIgnoreCase(value);
    }
}
