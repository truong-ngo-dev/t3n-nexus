package vn.t3nexus.customer.presentation.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.customer.application.customer.CreateCustomerProfile;
import vn.t3nexus.customer.presentation.internal.model.CustomerRegisteredRequest;

@RestController
@RequestMapping("/internal/customer")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CreateCustomerProfile.Handler createCustomerProfile;

    @PostMapping("/events/registered")
    public ResponseEntity<Void> onCustomerRegistered(@Valid @RequestBody CustomerRegisteredRequest request) {
        createCustomerProfile.handle(new CreateCustomerProfile.Command(request.userId()));
        return ResponseEntity.ok().build();
    }
}

