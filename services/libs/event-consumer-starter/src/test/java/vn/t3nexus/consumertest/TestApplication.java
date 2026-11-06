package vn.t3nexus.consumertest;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.MDC;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.eventconsumer.EventEnvelopeConsumer;
import vn.t3nexus.lib.eventconsumer.NonRetryableEventException;
import vn.t3nexus.lib.events.EventEnvelope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Ứng dụng tối thiểu cho test tích hợp: một listener dùng starter, một handler có hành vi do nội dung event điều khiển. */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    SimulatedHandler simulatedHandler(ObjectMapper objectMapper) {
        return new SimulatedHandler(objectMapper);
    }

    @Component
    static class Listener {
        private final EventEnvelopeConsumer consumer;
        private final SimulatedHandler handler;

        Listener(EventEnvelopeConsumer consumer, SimulatedHandler handler) {
            this.consumer = consumer;
            this.handler = handler;
        }

        @KafkaListener(topics = "${test.topic}")
        void on(String body) {
            consumer.consume(body, handler::handle);
            handler.mdcAfterConsume.add(MDC.get("traceId") + "|" + MDC.get("eventId"));
        }
    }

    /** Hành vi theo trường {@code mode} của nội dung: ok | transient (failTimes) | always | poison | failAfterEffectOnce. */
    public static class SimulatedHandler {
        private final ObjectMapper objectMapper;
        public final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        public final Set<String> effects = ConcurrentHashMap.newKeySet();
        public final AtomicInteger effectApplications = new AtomicInteger();
        public final List<String> processedOrder = Collections.synchronizedList(new ArrayList<>());
        public final Map<String, Map<String, String>> mdcInside = new ConcurrentHashMap<>();
        public final List<String> mdcAfterConsume = Collections.synchronizedList(new ArrayList<>());

        SimulatedHandler(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public int attemptsOf(String eventId) {
            AtomicInteger a = attempts.get(eventId);
            return a == null ? 0 : a.get();
        }

        void handle(EventEnvelope e) {
            int attempt = attempts.computeIfAbsent(e.eventId(), k -> new AtomicInteger()).incrementAndGet();
            mdcInside.put(e.eventId(), Map.of(
                    "traceId", String.valueOf(MDC.get("traceId")),
                    "spanId", String.valueOf(MDC.get("spanId")),
                    "eventId", String.valueOf(MDC.get("eventId")),
                    "eventType", String.valueOf(MDC.get("eventType"))));
            Map<?, ?> payload = objectMapper.convertValue(e.payload(), Map.class);
            String mode = String.valueOf(payload.get("mode"));
            switch (mode) {
                case "ok" -> apply(e.eventId());
                case "transient" -> {
                    if (attempt <= ((Number) payload.get("failTimes")).intValue()) {
                        throw new IllegalStateException("db tạm hỏng, mật khẩu=hunter2-do-not-leak");
                    }
                    apply(e.eventId());
                }
                case "always" -> throw new IllegalStateException("boom hunter2-do-not-leak");
                case "poison" -> throw new NonRetryableEventException("event vô nghĩa");
                case "failAfterEffectOnce" -> {
                    apply(e.eventId());                       // hiệu lực đã ghi (như đã commit)
                    if (attempt == 1) {
                        throw new IllegalStateException("chết trước khi ack");
                    }
                }
                default -> throw new IllegalArgumentException("mode lạ: " + mode);
            }
        }

        /** Khử trùng kiểu ràng buộc duy nhất trên mã event: lần hai không có hiệu lực. */
        private void apply(String eventId) {
            if (effects.add(eventId)) {
                effectApplications.incrementAndGet();
                processedOrder.add(eventId);
            }
        }
    }
}
