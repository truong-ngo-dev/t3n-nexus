package vn.t3nexus.identity.infrastructure.adapter.service.device;

import lombok.RequiredArgsConstructor;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.device.DeviceName;
import vn.t3nexus.identity.domain.device.DeviceNameDetector;
import vn.t3nexus.identity.domain.device.DeviceType;

@Component
@RequiredArgsConstructor
public class YauaaDeviceNameDetector implements DeviceNameDetector {

    private final UserAgentAnalyzer analyzer;

    @Override
    public DeviceName detect(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceName.unknown();
        }

        UserAgent agent = analyzer.parse(userAgent);

        String deviceClass = agent.getValue("DeviceClass");
        String osName      = agent.getValue("OperatingSystemName");
        String osVersion   = agent.getValue("OperatingSystemVersion");
        String agentName   = agent.getValue("AgentNameVersion");
        String deviceModel = agent.getValue("DeviceName");

        DeviceType type = resolveType(deviceClass, osName, agentName);
        String     name = buildName(agentName, osName, osVersion, deviceModel);

        return DeviceName.of(name, type);
    }

    private DeviceType resolveType(String deviceClass, String osName, String agentName) {
        String upperOs    = osName.toUpperCase();
        String upperAgent = agentName.toUpperCase();

        if (upperOs.contains("ANDROID")) return DeviceType.ANDROID;
        if (upperOs.contains("IOS"))     return DeviceType.IOS;
        if (upperOs.contains("MAC") && !deviceClass.equals("Desktop")) return DeviceType.IOS;

        if (deviceClass.equals("Desktop") || deviceClass.equals("Tablet")) {
            if (upperAgent.contains("ELECTRON")    ||
                upperAgent.contains("NWJS")        ||
                upperAgent.contains("JAVA")        ||
                upperAgent.contains("QTWEBENGINE") ||
                (upperAgent.contains("FIREFOX") && upperOs.contains("LINUX"))) {
                return DeviceType.DESKTOP_APP;
            }
            return DeviceType.WEB;
        }

        return DeviceType.OTHER;
    }

    private String buildName(String agentName, String osName, String osVersion, String deviceModel) {
        String agent   = isUnknown(agentName)   ? "Unknown Browser" : agentName;
        String version = isUnknown(osVersion)   ? "" : osVersion;
        String model   = isUnknown(deviceModel) ? "" : deviceModel;

        String display = String.format("%s on %s %s", agent, osName, version).trim();
        if (osName.contains("Mac")) {
            return display;
        }
        if (!model.isEmpty()) {
            return String.format("%s (%s)", model, osName);
        }
        return display;
    }

    private boolean isUnknown(String value) {
        return value == null        ||
               value.isBlank()     ||
               "??".equals(value)  ||
               "Unknown".equalsIgnoreCase(value);
    }
}
