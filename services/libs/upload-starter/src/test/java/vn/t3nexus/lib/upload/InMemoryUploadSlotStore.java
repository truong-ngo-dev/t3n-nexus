package vn.t3nexus.lib.upload;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryUploadSlotStore implements UploadSlotStore {

    private final Map<UUID, UploadSlot> slots = new LinkedHashMap<>();

    @Override
    public void save(UploadSlot slot) {
        slots.put(slot.id(), slot);
    }

    @Override
    public Optional<UploadSlot> findById(UUID id) {
        return Optional.ofNullable(slots.get(id));
    }

    @Override
    public int countActive(String purpose, UUID accountId, UUID targetId, Instant now) {
        return (int) slots.values().stream()
                .filter(s -> s.purpose().equals(purpose))
                .filter(s -> s.accountId().equals(accountId))
                .filter(s -> s.targetId().equals(targetId))
                .filter(s -> s.expiresAt().isAfter(now))
                .count();
    }

    @Override
    public boolean deleteById(UUID id) {
        return slots.remove(id) != null;
    }

    @Override
    public List<UploadSlot> findExpired(Instant before, int limit) {
        return slots.values().stream()
                .filter(s -> s.expiresAt().isBefore(before))
                .sorted(Comparator.comparing(UploadSlot::expiresAt))
                .limit(limit)
                .toList();
    }
}
