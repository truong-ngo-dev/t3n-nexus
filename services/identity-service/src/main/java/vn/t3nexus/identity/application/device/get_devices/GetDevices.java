package vn.t3nexus.identity.application.device.get_devices;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.identity.domain.device.Device;
import vn.t3nexus.identity.domain.device.DeviceFingerprint;
import vn.t3nexus.identity.domain.device.DeviceRepository;
import vn.t3nexus.identity.domain.device.UserAgentParser;
import vn.t3nexus.identity.domain.login_activity.LoginActivity;
import vn.t3nexus.identity.domain.login_activity.LoginActivityRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetDevices implements QueryHandler<GetDevices.Query, List<GetDevices.DeviceItem>> {

    public record Query(
            String userId,
            String deviceHash,
            String userAgent,
            String acceptLanguage
    ) {}

    public record DeviceItem(
            String  deviceId,
            String  displayName,
            String  browser,
            String  os,
            Instant lastSeenAt,
            String  lastAction,
            boolean isCurrent,
            boolean isTrusted
    ) {}

    private final DeviceRepository        deviceRepository;
    private final LoginActivityRepository loginActivityRepository;
    private final UserAgentParser         userAgentParser;

    @Override
    public List<DeviceItem> handle(Query query) {
        UserId userId = UserId.of(query.userId());

        String currentHash = resolveCurrentHash(query);

        List<Device> devices = deviceRepository.findActiveByUserId(userId);

        Set<String> historyIds = devices.stream()
                .map(Device::getLastHistoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, LoginActivity> activityMap = loginActivityRepository.findAllByIds(historyIds)
                .stream()
                .collect(Collectors.toMap(a -> a.getId().getValueAsString(), Function.identity()));

        return devices.stream().map(device -> {
            UserAgentParser.ParsedUserAgent ua = userAgentParser.parse(device.getFingerprint().getUserAgent());
            LoginActivity lastActivity = activityMap.get(device.getLastHistoryId());
            String  lastAction = lastActivity != null ? lastActivity.getResult().name() : null;
            boolean isCurrent  = currentHash != null && currentHash.equals(device.getFingerprint().getCompositeHash());
            return new DeviceItem(
                    device.getId().getValueAsString(),
                    device.getName().getValue(),
                    ua.browser(),
                    ua.os(),
                    device.getLastSeenAt(),
                    lastAction,
                    isCurrent,
                    device.isTrusted()
            );
        }).toList();
    }

    private String resolveCurrentHash(Query query) {
        if (query.deviceHash() == null || query.userAgent() == null) {
            return null;
        }
        return DeviceFingerprint.of(
                query.deviceHash(),
                query.userAgent(),
                query.acceptLanguage()
        ).getCompositeHash();
    }
}
