package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class OAuthSessionId extends AbstractId<String> implements Id<String> {

    public OAuthSessionId(String value) {
        super(value);
    }
}
