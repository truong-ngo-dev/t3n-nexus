package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class EmailVerificationId extends AbstractId<String> implements Id<String> {
    private EmailVerificationId(String value) { super(value); }

    public static EmailVerificationId of(String value) { return new EmailVerificationId(value); }
}
