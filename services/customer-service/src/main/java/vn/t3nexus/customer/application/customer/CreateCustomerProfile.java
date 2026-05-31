package vn.t3nexus.customer.application.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.customer.domain.customer.CustomerProfile;
import vn.t3nexus.customer.domain.customer.CustomerProfileId;
import vn.t3nexus.customer.domain.customer.CustomerProfileRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateCustomerProfile implements CommandHandler<CreateCustomerProfile.Command, CreateCustomerProfile.Reply> {

    private final CustomerProfileRepository customerProfileRepository;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    public Reply handle(Command command) {
        CustomerProfileId id = CustomerProfileId.of(ulidGenerator.generate());
        CustomerProfile profile = CustomerProfile.create(id, command.userId());
        customerProfileRepository.save(profile);
        log.info("[CreateCustomerProfile] profile created: profileId={}, userId={}, traceId={}", id.getValue(), command.userId(), MDC.get("traceId"));
        return new Reply(id.getValue());
    }

    public record Command(String userId) {}

    public record Reply(String customerProfileId) {}
}
