package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.model.ValueObject;

/**
 * Login context captured during authentication — passed to OAuthSession.issue() to build
 * SessionIssuedEvent. Not persisted as part of OAuthSession state.
 */
public record SessionIssuanceContext(
        String loginIdentifier, // submitted email/username
        String deviceHash,      // from JS hidden input ("" when unavailable)
        String userAgent,
        String acceptLanguage,
        String provider         // LOCAL | GOOGLE
) implements ValueObject {}
