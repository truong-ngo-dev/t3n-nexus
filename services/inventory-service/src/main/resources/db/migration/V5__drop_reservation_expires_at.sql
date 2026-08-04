-- ------------------------------------------------------------
-- reservation.expires_at — dead field, never read by any code (write-only since V1).
-- Original intent: standalone TTL sweep to auto-release stale PENDING reservations.
-- Superseded by order-service's own CREATED-state timeout (payment-checkout feature,
-- ADR-010 era) — order-service owns the "is this saga stuck" decision and publishes
-- OrderCancelled, which the existing OrderCancelledConsumer/ReleaseReservation flow
-- already handles correctly. A TTL sweep here would be unsafe anyway: ReservationStatus
-- has no CONFIRMED state, so a naive sweep would release stock for legitimately
-- confirmed orders too (they stay PENDING forever by design).
-- ------------------------------------------------------------
DROP INDEX IF EXISTS idx_reservation_status_expires;
ALTER TABLE reservation DROP COLUMN expires_at;
