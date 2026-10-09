package vn.t3nexus.oauth2.infrastructure.adapter.repository.user_account;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountErrorCode;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountStatus;
import vn.t3nexus.oauth2.infrastructure.persistence.user_account.UserAccountJpaRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chỉ những ca không thay được bằng test miền: ánh xạ lưu rồi đọc lại (KT-11) và lỗi trùng email do cơ sở dữ liệu sinh ra
 * rồi được kho dịch thành {@code EMAIL_TAKEN} (KT-12).
 * <br>Chạy trên Postgres đang bật theo {@code application.properties} (đã chạy Flyway khi khởi động ứng dụng). Mỗi test nằm
 * trong một giao dịch và được hoàn tác nên không để lại dữ liệu. Email có phần ngẫu nhiên để không đụng dữ liệu có sẵn.
 */
@SpringBootTest(classes = UserAccountRepositoryAdapterTest.TestConfig.class)
@Transactional
class UserAccountRepositoryAdapterTest {

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            TransactionAutoConfiguration.class})
    @EntityScan("vn.t3nexus.oauth2")
    @EnableJpaRepositories(basePackageClasses = UserAccountJpaRepository.class)
    @Import({UserAccountRepositoryAdapter.class, UserAccountMapper.class})
    static class TestConfig {
    }

    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");

    private final UuidV7IdGenerator ids = new UuidV7IdGenerator();

    @Autowired UserAccountRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void saved_account_reads_back_the_same_and_the_password_column_holds_only_the_hash() {
        Email email = randomEmail();
        PasswordHash hash = PasswordHash.ofHashed("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        UserAccount account = UserAccount.register(UserAccountId.of(ids.generate()), email, hash, NOW);

        repository.save(account);

        UserAccount loaded = repository.findById(account.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(account.getId());
        assertThat(loaded.getEmail()).isEqualTo(email);
        assertThat(loaded.getRole()).isEqualTo(Role.USER);
        assertThat(loaded.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(repository.existsByEmail(email)).isTrue();

        Object storedPassword = entityManager
                .createNativeQuery("select password_hash from user_accounts where id = :id")
                .setParameter("id", account.getId().getValue())
                .getSingleResult();
        assertThat(storedPassword).isEqualTo(hash.getHashedValue());
    }

    @Test
    void database_rejects_a_second_account_with_the_same_email_and_the_repository_reports_email_taken() {
        Email email = randomEmail();
        PasswordHash hash = PasswordHash.ofHashed("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
        repository.save(UserAccount.register(UserAccountId.of(ids.generate()), email, hash, NOW));
        UserAccount second = UserAccount.register(UserAccountId.of(ids.generate()), email, hash, NOW);

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(UserAccountException.class)
                .extracting(e -> ((UserAccountException) e).getErrorCode())
                .isEqualTo(UserAccountErrorCode.EMAIL_TAKEN);
    }

    private static Email randomEmail() {
        return Email.of("it-" + UUID.randomUUID() + "@example.com");
    }
}
