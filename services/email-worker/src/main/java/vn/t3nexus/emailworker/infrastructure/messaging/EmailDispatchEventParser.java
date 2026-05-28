package vn.t3nexus.emailworker.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.emailworker.application.email.EmailDispatchEvent;

@Component
@RequiredArgsConstructor
public class EmailDispatchEventParser {

    private final ObjectMapper objectMapper;

    public EmailDispatchEvent parse(String message) {
        JsonNode root = objectMapper.readTree(message);
        JsonNode data = root.has("schema") ? root.get("payload") : root;
        return objectMapper.treeToValue(data, EmailDispatchEvent.class);
    }
}
