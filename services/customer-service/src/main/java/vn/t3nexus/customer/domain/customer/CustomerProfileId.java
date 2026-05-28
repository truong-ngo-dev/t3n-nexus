package vn.t3nexus.customer.domain.customer;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class CustomerProfileId extends AbstractId<String> implements Id<String> {

    private CustomerProfileId(String value) {
        super(value);
    }

    public static CustomerProfileId of(String id) {
        return new CustomerProfileId(id);
    }
}
