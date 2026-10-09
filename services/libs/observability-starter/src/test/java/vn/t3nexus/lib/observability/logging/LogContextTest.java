package vn.t3nexus.lib.observability.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTest {

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void put_adds_the_labels_prefix_and_user_and_operation_use_their_own_keys() {
        LogContext.put("orderId", "ORD-1");
        LogContext.user("u-1");
        LogContext.operation("exportReport");

        assertThat(MDC.get("labels.orderId")).isEqualTo("ORD-1");
        assertThat(MDC.get("user.id")).isEqualTo("u-1");
        assertThat(MDC.get("labels.operation")).isEqualTo("exportReport");
    }

    @Test
    void null_value_removes_the_key_and_invalid_names_are_ignored_without_throwing() {
        LogContext.put("orderId", "ORD-1");
        LogContext.put("orderId", null);

        assertThat(MDC.get("labels.orderId")).isNull();
        assertThat(LogContext.put("bad key", "x")).isFalse();
        assertThat(LogContext.put("a.b", "x")).isFalse();
        assertThat(LogContext.put("", "x")).isFalse();
        assertThat(LogContext.put(null, "x")).isFalse();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void more_than_the_limit_of_labels_are_dropped_but_existing_ones_can_still_be_updated() {
        for (int i = 0; i < LogContext.MAX_LABELS; i++) LogContext.put("k" + i, "v");

        assertThat(LogContext.put("extra", "v")).isFalse();
        assertThat(MDC.get("labels.extra")).isNull();
        assertThat(LogContext.put("k0", "new")).isTrue();
        assertThat(MDC.get("labels.k0")).isEqualTo("new");
    }

    @Test
    void values_are_cleaned_of_control_characters_and_truncated() {
        LogContext.put("note", "line1\nFAKE LOG LINE\r\tend");
        LogContext.put("long", "x".repeat(500));

        assertThat(MDC.get("labels.note")).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t");
        assertThat(MDC.get("labels.long")).startsWith("x".repeat(LogContext.MAX_VALUE_LENGTH)).contains("tổng 500");
    }

    @Test
    void clear_removes_only_labels_and_user_and_keeps_trace_context() {
        MDC.put("traceId", "t-1");
        LogContext.put("orderId", "ORD-1");
        LogContext.user("u-1");

        LogContext.clear();

        assertThat(MDC.get("traceId")).isEqualTo("t-1");
        assertThat(MDC.get("labels.orderId")).isNull();
        assertThat(MDC.get("user.id")).isNull();
    }

    @Test
    void scope_restores_the_previous_state_so_nesting_does_not_erase_outer_keys() {
        LogContext.put("outer", "1");

        try (var ignored = LogContext.scope()) {
            LogContext.put("inner", "2");
            LogContext.put("outer", "changed");
            try (var ignored2 = LogContext.scope()) {
                LogContext.put("deep", "3");
            }
            assertThat(MDC.get("labels.deep")).isNull();
            assertThat(MDC.get("labels.inner")).isEqualTo("2");
        }

        assertThat(MDC.get("labels.outer")).isEqualTo("1");
        assertThat(MDC.get("labels.inner")).isNull();
    }

    @Test
    void scope_restores_even_when_the_block_throws() {
        LogContext.put("outer", "1");

        try (var ignored = LogContext.scope()) {
            LogContext.put("inner", "2");
            throw new IllegalStateException("boom");
        } catch (IllegalStateException expected) {
            // nuốt để kiểm trạng thái sau đó
        }

        assertThat(MDC.get("labels.inner")).isNull();
        assertThat(MDC.get("labels.outer")).isEqualTo("1");
    }

    @Test
    void wrap_carries_the_callers_context_to_another_thread_and_leaves_the_pool_thread_clean() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            MDC.put("traceId", "t-1");
            LogContext.put("orderId", "ORD-1");

            Future<String> seen = pool.submit(LogContext.wrap(() -> MDC.get("labels.orderId") + "|" + MDC.get("traceId")));
            assertThat(seen.get()).isEqualTo("ORD-1|t-1");

            // luồng pool không giữ lại gì sau khi tác vụ xong
            Future<String> after = pool.submit(() -> String.valueOf(MDC.get("labels.orderId")));
            assertThat(after.get()).isEqualTo("null");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void a_branch_that_adds_its_own_key_does_not_affect_the_parent_or_its_siblings() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            LogContext.put("orderId", "ORD-1");

            Future<String> a = pool.submit(LogContext.wrap(() -> {
                LogContext.put("branch", "A");
                return MDC.get("labels.branch");
            }));
            Future<String> b = pool.submit(LogContext.wrap(() -> String.valueOf(MDC.get("labels.branch"))));

            assertThat(a.get()).isEqualTo("A");
            assertThat(b.get()).isEqualTo("null");
            assertThat(MDC.get("labels.branch")).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void wrap_captures_at_submit_time_not_at_run_time() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            LogContext.put("orderId", "ORD-1");
            var task = LogContext.wrap(() -> MDC.get("labels.orderId"));
            LogContext.put("orderId", "ORD-2");

            assertThat(pool.submit(task).get()).isEqualTo("ORD-1");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void the_task_decorator_gives_spring_executors_the_same_behaviour() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            LogContext.put("orderId", "ORD-1");
            Runnable decorated = new MdcTaskDecorator().decorate(() -> {
                assertThat(MDC.get("labels.orderId")).isEqualTo("ORD-1");
            });

            pool.submit(decorated).get();
        } finally {
            pool.shutdownNow();
        }
    }
}
