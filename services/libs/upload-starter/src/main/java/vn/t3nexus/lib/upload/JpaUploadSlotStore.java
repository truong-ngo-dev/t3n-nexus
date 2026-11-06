package vn.t3nexus.lib.upload;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cài đặt {@link UploadSlotStore} bằng JPA trên cơ sở dữ liệu của dịch vụ dùng thư viện; tham gia giao dịch của BC. */
class JpaUploadSlotStore implements UploadSlotStore {

    private final UploadSlotJpaRepository repository;

    JpaUploadSlotStore(UploadSlotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(UploadSlot slot) {
        repository.saveAndFlush(new UploadSlotEntity(slot));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UploadSlot> findById(UUID id) {
        return repository.findById(id).map(UploadSlotEntity::toSlot);
    }

    @Override
    @Transactional(readOnly = true)
    public int countActive(String purpose, UUID accountId, UUID targetId, Instant now) {
        return (int) repository.countActive(purpose, accountId, targetId, now);
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        return repository.deleteSlot(id) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UploadSlot> findExpired(Instant before, int limit) {
        return repository.findByExpiresAtBeforeOrderByExpiresAtAsc(before, PageRequest.of(0, limit))
                .stream().map(UploadSlotEntity::toSlot).toList();
    }
}
