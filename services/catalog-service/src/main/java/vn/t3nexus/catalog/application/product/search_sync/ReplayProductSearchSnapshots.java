package vn.t3nexus.catalog.application.product.search_sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.List;

/**
 * Phát lại {@link ProductSearchSnapshotEvent} cho mọi sản phẩm đã từng publish — backfill/rebuild index
 * bên search đi đúng pipeline event, không có đường code riêng (analysis.md §6). Mỗi sản phẩm 1 transaction
 * riêng: 1 sản phẩm lỗi không kéo rollback cả lô, outbox được commit dần thay vì dồn 1 transaction khổng lồ.
 * <br>Chạy đồng bộ trong request — đủ cho quy mô dev. Replay chồng lên event live vẫn an toàn vì mỗi lần
 * phát đều tăng {@code search_version}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayProductSearchSnapshots
        implements CommandHandler<ReplayProductSearchSnapshots.Command, ReplayProductSearchSnapshots.Result> {

    private static final int PAGE_SIZE = 200;

    private final ProductSearchSnapshotPort snapshotPort;
    private final PublishProductSearchSnapshot publishProductSearchSnapshot;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Result handle(Command command) {
        String afterId = "";
        int published = 0;
        int failed = 0;

        while (true) {
            List<String> ids = snapshotPort.findPublishedIdsAfter(afterId, PAGE_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            for (String productId : ids) {
                try {
                    transactionTemplate.executeWithoutResult(status -> publishProductSearchSnapshot.publish(productId));
                    published++;
                } catch (RuntimeException e) {
                    failed++;
                    log.error("[ReplayProductSearchSnapshots] failed: productId={}", productId, e);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }

        log.info("[ReplayProductSearchSnapshots] done: published={}, failed={}, traceId={}",
                published, failed, MDC.get("traceId"));

        return new Result(published, failed);
    }

    public record Command() {}

    public record Result(int published, int failed) {}
}
