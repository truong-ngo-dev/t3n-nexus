package vn.t3nexus.oauth2.infrastructure.adapter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.oauth2.domain.session.OAuthSessionExpiryService;
import vn.t3nexus.oauth2.domain.session.SessionsBulkExpiredEvent;

import java.util.List;
import java.util.Map;

/**
 * Tìm oauth_sessions có idp_session_id không còn trong SPRING_SESSION (IDP session đã expire/bị xóa),
 * xóa batch oauth2_authorization + oauth_sessions, trả một event tổng hợp.
 *
 * Không đi qua aggregate lifecycle vì đây là bulk cleanup — aggregate.expire() sinh per-session event
 * không phù hợp cho path này.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdbcOAuthSessionExpiryService implements OAuthSessionExpiryService {

    private final NamedParameterJdbcOperations namedJdbc;

    @Override
    @Transactional
    public SessionsBulkExpiredEvent expireOrphaned() {
        List<Map<String, Object>> rows = namedJdbc.getJdbcOperations().queryForList("""
                SELECT os.id, os.authorization_id
                FROM oauth_sessions os
                LEFT JOIN SPRING_SESSION ss ON ss.SESSION_ID = os.idp_session_id
                WHERE ss.SESSION_ID IS NULL
                  AND os.idp_session_id IS NOT NULL
                """);

        if (rows.isEmpty()) return new SessionsBulkExpiredEvent(List.of());

        List<String> ossIds           = rows.stream().map(r -> (String) r.get("id")).toList();
        List<String> authorizationIds = rows.stream().map(r -> (String) r.get("authorization_id")).toList();

        log.info("[OAuthSessionExpiryService] expiring {} orphaned sessions", ossIds.size());

        namedJdbc.update(
                "DELETE FROM oauth2_authorization WHERE id IN (:ids)",
                Map.of("ids", authorizationIds));

        namedJdbc.update(
                "DELETE FROM oauth_sessions WHERE id IN (:ids)",
                Map.of("ids", ossIds));

        return new SessionsBulkExpiredEvent(ossIds);
    }
}
