package vn.t3nexus.inappworker.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inappworker.application.inapp.InappDispatchEvent;

@Component
@RequiredArgsConstructor
public class InappDispatchEventParser {

    private final ObjectMapper objectMapper;

    public InappDispatchEvent parse(String message) {
        JsonNode root = objectMapper.readTree(message);
        JsonNode data = root.has("schema") ? root.get("payload") : root;
        return objectMapper.treeToValue(data, InappDispatchEvent.class);
    }
}
