package vn.t3nexus.lib.eventconsumer.testing;

import vn.t3nexus.lib.events.EventEnvelope;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bộ khung kiểm thử khử trùng cho handler của BC (ADR-0008 mục 4): mọi BC nhận event phải có ít nhất một test dùng bộ
 * khung này cho mỗi handler. Handler tự lo khử trùng (ràng buộc duy nhất hoặc phiên bản); bộ khung chỉ chứng minh điều đó
 * bằng cách giao event theo hai kịch bản và so trạng thái cuối.
 * <ul>
 *   <li>{@link #assertDuplicateDeliveryIsHarmless}: giao một lần và giao hai lần phải ra cùng trạng thái.</li>
 *   <li>{@link #assertReorderingIsHarmless}: giao theo thứ tự và theo thứ tự đảo phải ra cùng trạng thái (cho handler mang
 *       trạng thái của cùng một thực thể; handler mỗi event một bản ghi độc lập không cần).</li>
 * </ul>
 * Trạng thái đọc qua {@code state} phải so sánh được bằng {@code equals} (bản chụp bất biến: {@code Set.copyOf}, bản ghi...).
 * Bộ khung gọi {@code reset} trước mỗi kịch bản và sau lần kiểm cuối.
 *
 * @param <S> kiểu bản chụp trạng thái
 */
public class DeliveryHarness<S> {

    private final Consumer<EventEnvelope> handler;
    private final Supplier<S> state;
    private final Runnable reset;

    public DeliveryHarness(Consumer<EventEnvelope> handler, Supplier<S> state, Runnable reset) {
        this.handler = handler;
        this.state = state;
        this.reset = reset;
    }

    public void assertDuplicateDeliveryIsHarmless(EventEnvelope event) {
        reset.run();
        handler.accept(event);
        S once = state.get();
        handler.accept(event);
        S twice = state.get();
        reset.run();
        if (!once.equals(twice)) {
            throw new AssertionError("Nhận trùng event " + event.eventId() + " làm đổi kết quả: sau lần 1 = " + once
                    + ", sau lần 2 = " + twice);
        }
    }

    public void assertReorderingIsHarmless(EventEnvelope first, EventEnvelope second) {
        reset.run();
        handler.accept(first);
        handler.accept(second);
        S inOrder = state.get();
        reset.run();
        handler.accept(second);
        handler.accept(first);
        S reversed = state.get();
        reset.run();
        if (!inOrder.equals(reversed)) {
            throw new AssertionError("Đảo thứ tự event " + first.eventId() + " và " + second.eventId()
                    + " làm đổi kết quả: đúng thứ tự = " + inOrder + ", đảo = " + reversed);
        }
    }
}
