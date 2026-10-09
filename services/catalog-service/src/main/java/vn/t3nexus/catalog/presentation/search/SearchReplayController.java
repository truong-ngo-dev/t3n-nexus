package vn.t3nexus.catalog.presentation.search;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.catalog.application.product.search_sync.ReplayProductSearchSnapshots;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

/** Admin — phát lại snapshot cho search-service (backfill / rebuild index). */
@RestController
@RequiredArgsConstructor
public class SearchReplayController {

    private final ReplayProductSearchSnapshots replayProductSearchSnapshots;

    @PostMapping("/api/admin/search/replay/products")
    public ApiResponse<ReplayResponse> replayProducts() {
        ReplayProductSearchSnapshots.Result result =
                replayProductSearchSnapshots.handle(new ReplayProductSearchSnapshots.Command());
        return ApiResponse.ok(new ReplayResponse(result.published(), result.failed()));
    }

    public record ReplayResponse(int published, int failed) {}
}
