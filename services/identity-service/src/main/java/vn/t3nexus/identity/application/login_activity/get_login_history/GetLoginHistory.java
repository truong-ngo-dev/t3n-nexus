package vn.t3nexus.identity.application.login_activity.get_login_history;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.identity.domain.device.UserAgentParser;
import vn.t3nexus.identity.domain.login_activity.LoginActivity;
import vn.t3nexus.identity.domain.login_activity.LoginActivityRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetLoginHistory implements QueryHandler<GetLoginHistory.Query, List<GetLoginHistory.HistoryItem>> {

    public record Query(String userId, int page, int size) {}

    public record HistoryItem(String action, String ip, String browser, String os, Instant createdAt) {}

    private final LoginActivityRepository loginActivityRepository;
    private final UserAgentParser         userAgentParser;

    @Override
    public List<HistoryItem> handle(Query query) {
        List<LoginActivity> activities = loginActivityRepository.findPageByUserId(
                UserId.of(query.userId()), query.page(), query.size()
        );
        return activities.stream()
                .map(activity -> {
                    UserAgentParser.ParsedUserAgent ua = userAgentParser.parse(activity.getUserAgent());
                    return new HistoryItem(
                            activity.getResult().name(),
                            activity.getIpAddress(),
                            ua.browser(),
                            ua.os(),
                            activity.getCreatedAt()
                    );
                })
                .toList();
    }
}
