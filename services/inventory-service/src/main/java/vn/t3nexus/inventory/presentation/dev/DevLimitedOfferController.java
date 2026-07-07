package vn.t3nexus.inventory.presentation.dev;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequestMapping("/dev/limited-offers")
@RequiredArgsConstructor
public class DevLimitedOfferController {

    private final JdbcTemplate jdbc;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seed(@RequestBody SeedRequest request) {
        jdbc.update("""
                INSERT INTO limited_offer
                    (id, sku_id, max_quantity, window_seconds, status, activated_at, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'ACTIVE', now(), now(), now())
                ON CONFLICT (sku_id) DO UPDATE
                    SET max_quantity   = EXCLUDED.max_quantity,
                        window_seconds = EXCLUDED.window_seconds,
                        status         = 'ACTIVE',
                        activated_at   = now(),
                        updated_at     = now()
                """,
                request.skuId(), request.maxQuantity(), request.windowSeconds());
    }

    @DeleteMapping("/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String skuId) {
        jdbc.update("""
                UPDATE limited_offer
                   SET status = 'ENDED', ended_at = now(), updated_at = now()
                 WHERE sku_id = ? AND status = 'ACTIVE'
                """, skuId);
    }

    public record SeedRequest(String skuId, int maxQuantity, int windowSeconds) {}
}
