package vn.t3nexus.identity.domain.login_activity;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class LoginActivityId extends AbstractId<String> implements Id<String> {

    private LoginActivityId(String value) { super(value); }

    public static LoginActivityId of(String value) { return new LoginActivityId(value); }
}
