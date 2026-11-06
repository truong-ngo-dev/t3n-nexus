package vn.t3nexus.lib.eventconsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeFactory;
import vn.t3nexus.lib.events.EventEnvelopeReader;
import vn.t3nexus.lib.events.InvalidEventEnvelopeException;
import vn.t3nexus.lib.events.SensitiveFieldPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeConsumerTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final EventEnvelopeConsumer consumer = new EventEnvelopeConsumer(new EventEnvelopeReader(mapper));
    private final EventEnvelopeFactory factory = new EventEnvelopeFactory(mapper, SensitiveFieldPolicy.strict());

    private String body(String traceId, String spanId) {
        return factory.toJson(factory.create("e-1", "Probe", "agg-1", "ProbeEvent", Instant.parse("2026-10-24T00:00:00Z"),
                traceId, spanId, 1, Map.of("k", "v")));
    }

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void passesTheEnvelopeToTheHandlerWithTraceContextSet() {
        List<String> seen = new ArrayList<>();

        consumer.consume(body("t-1", "s-1"), (EventEnvelope e) ->
                seen.add(e.eventId() + "|" + MDC.get("traceId") + "|" + MDC.get("spanId") + "|" + MDC.get("eventId") + "|" + MDC.get("eventType")));

        assertThat(seen).containsExactly("e-1|t-1|s-1|e-1|ProbeEvent");
    }

    @Test
    void clearsTheContextAfterTheHandlerReturns() {
        consumer.consume(body("t-1", "s-1"), e -> { });

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
        assertThat(MDC.get("eventType")).isNull();
    }

    @Test
    void clearsTheContextEvenWhenTheHandlerThrowsAndRethrowsTheSameException() {
        IllegalStateException boom = new IllegalStateException("boom");

        assertThatThrownBy(() -> consumer.consume(body("t-1", "s-1"), e -> { throw boom; })).isSameAs(boom);

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
    }

    @Test
    void doesNotLeakAnEarlierEventsContextIntoALaterOneWithoutTraceIds() {
        List<String> seen = new ArrayList<>();
        consumer.consume(body("t-1", "s-1"), e -> { });

        consumer.consume(body(null, null), e -> seen.add(MDC.get("traceId") + "|" + MDC.get("spanId")));

        assertThat(seen).containsExactly("null|null");
    }

    @Test
    void anInvalidBodyIsRejectedBeforeTheHandlerIsCalled() {
        List<String> seen = new ArrayList<>();

        assertThatThrownBy(() -> consumer.consume("{\"password\":\"x\"", e -> seen.add("called")))
                .isInstanceOf(InvalidEventEnvelopeException.class);

        assertThat(seen).isEmpty();
    }
}
