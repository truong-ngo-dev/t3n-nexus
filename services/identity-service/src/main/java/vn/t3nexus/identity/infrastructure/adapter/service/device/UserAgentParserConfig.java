package vn.t3nexus.identity.infrastructure.adapter.service.device;

import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserAgentParserConfig {

    @Bean
    public UserAgentAnalyzer userAgentAnalyzer() {
        return UserAgentAnalyzer
                .newBuilder()
                .withCache(10000)
                .withField("DeviceClass")
                .withField("DeviceName")
                .withField("OperatingSystemName")
                .withField("OperatingSystemVersion")
                .withField("AgentNameVersion")
                .build();
    }
}
