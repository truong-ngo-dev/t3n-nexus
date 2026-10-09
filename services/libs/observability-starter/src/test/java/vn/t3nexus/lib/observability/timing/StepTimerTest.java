package vn.t3nexus.lib.observability.timing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepTimerTest {

    private static final long MS = 1_000_000L;

    private final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("test.StepTimer");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final AtomicLong now = new AtomicLong();

    @BeforeEach
    void attach() {
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    private String line(int index) {
        return appender.list.get(index).getFormattedMessage();
    }

    @Test
    void marks_report_the_time_since_the_previous_mark_and_the_running_total_with_the_size() {
        var timer = StepTimer.start(logger, "ExportReport", now::get);

        now.addAndGet(2310 * MS);
        timer.step("load-rows", 48200);
        now.addAndGet(5120 * MS);
        timer.step("build-excel", 1_873_022);
        timer.close();

        assertThat(appender.list).hasSize(3);
        assertThat(line(0)).isEqualTo("[ExportReport] step=load-rows took=2310ms total=2310ms size=48200");
        assertThat(line(1)).isEqualTo("[ExportReport] step=build-excel took=5120ms total=7430ms size=1873022");
        assertThat(line(2)).isEqualTo("[ExportReport] done total=7430ms");
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
    }

    @Test
    void a_mark_without_size_omits_the_size() {
        var timer = StepTimer.start(logger, "Op", now::get);
        now.addAndGet(5 * MS);

        timer.step("only");

        assertThat(line(0)).isEqualTo("[Op] step=only took=5ms total=5ms");
    }

    @Test
    void the_block_form_measures_exactly_the_block_and_returns_its_value() {
        var timer = StepTimer.start(logger, "Op", now::get);
        now.addAndGet(100 * MS); // thời gian trước khối không bị tính

        String result = timer.step("work", () -> {
            now.addAndGet(30 * MS);
            return "value";
        });

        assertThat(result).isEqualTo("value");
        assertThat(line(0)).isEqualTo("[Op] step=work took=30ms total=130ms");
    }

    @Test
    void the_block_form_still_logs_the_time_when_the_block_throws_and_rethrows() {
        var timer = StepTimer.start(logger, "Op", now::get);

        assertThatThrownBy(() -> timer.step("failing", () -> {
            now.addAndGet(7 * MS);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(line(0)).isEqualTo("[Op] step=failing took=7ms total=7ms");
    }

    @Test
    void the_runnable_block_form_works() {
        var timer = StepTimer.start(logger, "Op", now::get);
        var ran = new boolean[1];

        timer.step("work", () -> {
            now.addAndGet(3 * MS);
            ran[0] = true;
        });

        assertThat(ran[0]).isTrue();
        assertThat(line(0)).isEqualTo("[Op] step=work took=3ms total=3ms");
    }

    @Test
    void try_with_resources_writes_the_total_even_when_the_body_throws() {
        assertThatThrownBy(() -> {
            try (var timer = StepTimer.start(logger, "Op", now::get)) {
                now.addAndGet(9 * MS);
                throw new IllegalStateException("boom");
            }
        }).isInstanceOf(IllegalStateException.class);

        assertThat(line(0)).isEqualTo("[Op] done total=9ms");
    }

    @Test
    void when_debug_is_off_nothing_is_logged_the_clock_is_never_read_and_blocks_still_run() {
        logger.setLevel(Level.INFO);
        var clockReads = new AtomicLong();
        var timer = StepTimer.start(logger, "Op", () -> {
            clockReads.incrementAndGet();
            return 0L;
        });

        timer.step("a");
        timer.step("b", 10);
        int value = timer.step("c", () -> 42);
        timer.close();

        assertThat(value).isEqualTo(42);
        assertThat(appender.list).isEmpty();
        assertThat(clockReads).hasValue(0);
    }
}
