package vn.t3nexus.identity.domain.user;

import org.springframework.util.Assert;
import vn.t3nexus.lib.common.domain.model.ValueObject;

public class UserPassword implements ValueObject {

    private final String hashedValue;

    private UserPassword(String hashedValue) {
        this.hashedValue = hashedValue;
    }

    public static UserPassword ofHashed(String hashedValue) {
        Assert.hasText(hashedValue, "hashedValue is required");
        return new UserPassword(hashedValue);
    }

    public String getHashedValue() {
        return hashedValue;
    }
}
