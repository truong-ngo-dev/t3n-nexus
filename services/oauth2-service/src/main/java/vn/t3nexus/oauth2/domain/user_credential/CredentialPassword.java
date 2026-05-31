package vn.t3nexus.oauth2.domain.user_credential;

import org.springframework.util.Assert;
import vn.t3nexus.lib.common.domain.model.ValueObject;

public class CredentialPassword implements ValueObject {

    private final String hashedValue;

    private CredentialPassword(String hashedValue) {
        this.hashedValue = hashedValue;
    }

    public static CredentialPassword ofHashed(String hashedValue) {
        Assert.hasText(hashedValue, "hashedValue is required");
        return new CredentialPassword(hashedValue);
    }

    public String getHashedValue() {
        return hashedValue;
    }
}
