package vn.t3nexus.oauth2.seed;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.infrastructure.PostgresContainerHolder;
import vn.t3nexus.oauth2.infrastructure.adapter.service.ConfiguredInternalEmailPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-01 đến KT-07 và KT-11 của feature 04 (Quản trị có sẵn): chạy <b>chính tệp</b> {@code infra/seed/admin.sql} bằng {@code psql}
 * trong container Postgres của test, trên một cơ sở dữ liệu riêng đã chạy Flyway từ trống, rồi đọc bảng {@code user_accounts}.
 * Không có Spring: script là thứ được kiểm.
 */
class SeedAdminScriptTest {

    private static final Path SCRIPT = Path.of("..", "..", "infra", "seed", "admin.sql").toAbsolutePath().normalize();
    private static final String IN_CONTAINER = "/tmp/seed-admin.sql";
    private static final String EMAIL = "quantri@t3nexus.com.vn";
    /** Chuỗi đúng dạng bcrypt hệ số 10 (60 ký tự), không phải hash của mật khẩu nào cụ thể. */
    private static final String HASH_1 = "$2a$10$" + "a".repeat(53);
    private static final String HASH_2 = "$2a$10$" + "b".repeat(53);

    private String dbName;

    @BeforeAll
    static void scriptExists() {
        assertThat(SCRIPT).as("thiếu script khởi tạo Quản trị").exists();
        PostgresContainerHolder.POSTGRES.copyFileToContainer(MountableFile.forHostPath(SCRIPT), IN_CONTAINER);
    }

    @BeforeEach
    void newDatabase() throws SQLException {
        dbName = "seed_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = connection("postgres"); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + dbName);
        }
        Flyway.configure()
                .dataSource(jdbcUrl(dbName), PostgresContainerHolder.POSTGRES.getUsername(), PostgresContainerHolder.POSTGRES.getPassword())
                .load().migrate();
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        try (Connection c = connection("postgres"); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        }
    }

    // KT-01
    @Test
    void createsExactlyOneActiveAdminWithTheGivenHash() throws Exception {
        ExecResult r = run(EMAIL, HASH_1, null);

        assertThat(r.getExitCode()).as(r.getStderr()).isZero();
        List<Row> rows = rows();
        assertThat(rows).hasSize(1);
        Row row = rows.get(0);
        assertThat(row.email()).isEqualTo(EMAIL);
        assertThat(row.role()).isEqualTo("ADMIN");
        assertThat(row.status()).isEqualTo("ACTIVE");
        assertThat(row.registrationMethod()).isEqualTo("CREDENTIAL");
        assertThat(row.mfaEnabled()).isFalse();
        assertThat(row.passwordHash()).isEqualTo(HASH_1);
        assertThat(r.getStdout()).contains("seed-admin: created");
    }

    // KT-02
    @Test
    void secondRunAddsNothingAndKeepsTheFirstHash() throws Exception {
        run(EMAIL, HASH_1, null);

        ExecResult second = run(EMAIL, HASH_2, null);

        assertThat(second.getExitCode()).as(second.getStderr()).isZero();
        assertThat(rows()).singleElement().satisfies(row -> assertThat(row.passwordHash()).isEqualTo(HASH_1));
        assertThat(second.getStdout()).contains("seed-admin: exists").doesNotContain("seed-admin: created");
    }

    // KT-02 (dòng sẵn có không phải ADMIN: chỉ báo, không sửa)
    @Test
    void existingNonAdminRowIsReportedAndLeftUntouched() throws Exception {
        try (Connection c = connection(dbName); Statement s = c.createStatement()) {
            s.execute("INSERT INTO user_accounts (id, email, password_hash, role, registration_method, status, mfa_enabled, created_at, updated_at) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + EMAIL + "', '" + HASH_1 + "', 'USER', 'CREDENTIAL', 'ACTIVE', false, now(), now())");
        }

        ExecResult r = run(EMAIL, HASH_2, null);

        assertThat(r.getExitCode()).as(r.getStderr()).isZero();
        assertThat(r.getStdout()).contains("seed-admin: exists").contains("role=USER");
        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row.role()).isEqualTo("USER");
            assertThat(row.passwordHash()).isEqualTo(HASH_1);
        });
    }

    // KT-03
    @Test
    void emailIsNormalizedAndAnotherSpellingOfItDoesNotAddARow() throws Exception {
        ExecResult first = run("  Quantri@T3Nexus.com.VN ", HASH_1, null);
        assertThat(first.getExitCode()).as(first.getStderr()).isZero();
        assertThat(rows()).singleElement().satisfies(row -> assertThat(row.email()).isEqualTo(EMAIL));

        run(EMAIL, HASH_2, null);

        assertThat(rows()).hasSize(1);
    }

    // KT-04
    @Test
    void emailOutsideTheInternalDomainIsRejectedAndNothingIsWritten() throws Exception {
        for (String email : List.of("quantri@gmail.com", "quantri@not-t3nexus.com.vn", "quantri@t3nexus.com.vn.evil.com")) {
            ExecResult r = run(email, HASH_1, null);

            assertThat(r.getExitCode()).as(email).isNotZero();
            assertThat(rows()).as(email).isEmpty();
        }
    }

    // KT-05
    @Test
    void hashThatIsNotBcryptIsRejectedAndNothingIsWritten() throws Exception {
        for (String hash : List.of("MatKhau123", "$2a$10$abc", "$2a$10$" + "a".repeat(60), "$1$10$" + "a".repeat(53), "")) {
            ExecResult r = run(EMAIL, hash, null);

            assertThat(r.getExitCode()).as("hash=" + hash).isNotZero();
            assertThat(rows()).as("hash=" + hash).isEmpty();
        }
    }

    // KT-06
    @Test
    void missingParametersFailAndNothingIsWritten() throws Exception {
        assertThat(run(null, HASH_1, null).getExitCode()).isNotZero();
        assertThat(run(EMAIL, null, null).getExitCode()).isNotZero();
        assertThat(run(null, null, null).getExitCode()).isNotZero();
        assertThat(rows()).isEmpty();
    }

    // KT-07
    @Test
    void accountIdIsAUuidVersion7WithCurrentTime() throws Exception {
        Instant before = Instant.now();
        run(EMAIL, HASH_1, null);
        Instant after = Instant.now();

        UUID id = rows().get(0).id();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        long millis = id.getMostSignificantBits() >>> 16;
        assertThat(millis).isBetween(before.toEpochMilli() - 5_000, after.toEpochMilli() + 5_000);
    }

    // KT-11
    @Test
    void defaultDomainMatchesTheServiceDefault() throws Exception {
        String serviceDefault = serviceDefaultDomain();
        String email = "quantri@" + serviceDefault;
        assertThat(new ConfiguredInternalEmailPolicy(List.of(serviceDefault)).isInternal(Email.of(email))).isTrue();

        ExecResult r = run(email, HASH_1, null);

        assertThat(r.getExitCode()).as(r.getStderr()).isZero();
        assertThat(rows()).singleElement().satisfies(row -> assertThat(row.email()).isEqualTo(email));
    }

    // KT-10 (INV-ATH-11): dữ liệu khởi tạo không nằm trong migration của Flyway
    @Test
    void noFlywayMigrationInsertsIntoUserAccounts() throws IOException {
        Path migrations = Path.of("src", "main", "resources", "db", "migration");
        Pattern insert = Pattern.compile("(?is)insert\\s+into\\s+(public\\.)?user_accounts\\b");
        try (var files = java.nio.file.Files.list(migrations)) {
            List<Path> offenders = files.filter(p -> p.toString().endsWith(".sql")).filter(p -> {
                try {
                    return insert.matcher(java.nio.file.Files.readString(p)).find();
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList();
            assertThat(offenders).as("migration chèn dòng user_accounts").isEmpty();
        }
    }

    // ---- hỗ trợ ----

    /** Giá trị mặc định của {@code app.internal-email-domains} trong {@code application.properties} của dịch vụ. */
    private static String serviceDefaultDomain() throws IOException {
        Properties props = new Properties();
        try (InputStream in = SeedAdminScriptTest.class.getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        Matcher m = Pattern.compile("\\$\\{[A-Z_]+:([^}]+)}").matcher(props.getProperty("app.internal-email-domains"));
        assertThat(m.matches()).as("app.internal-email-domains phải có dạng ${ENV:mặc-định}").isTrue();
        return m.group(1);
    }

    private ExecResult run(String email, String hash, String domain) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("psql", "-U", PostgresContainerHolder.POSTGRES.getUsername(), "-d", dbName));
        if (email != null) cmd.addAll(List.of("-v", "email=" + email));
        if (hash != null) cmd.addAll(List.of("-v", "hash=" + hash));
        if (domain != null) cmd.addAll(List.of("-v", "domain=" + domain));
        cmd.addAll(List.of("-f", IN_CONTAINER));
        return PostgresContainerHolder.POSTGRES.execInContainer(cmd.toArray(String[]::new));
    }

    private List<Row> rows() throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection c = connection(dbName); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, email, password_hash, role, status, registration_method, mfa_enabled FROM user_accounts")) {
            while (rs.next()) {
                rows.add(new Row(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"), rs.getString("role"),
                        rs.getString("status"), rs.getString("registration_method"), rs.getBoolean("mfa_enabled")));
            }
        }
        return rows;
    }

    private static Connection connection(String db) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(db), PostgresContainerHolder.POSTGRES.getUsername(), PostgresContainerHolder.POSTGRES.getPassword());
    }

    private static String jdbcUrl(String db) {
        return "jdbc:postgresql://" + PostgresContainerHolder.POSTGRES.getHost() + ":" + PostgresContainerHolder.POSTGRES.getMappedPort(5432) + "/" + db;
    }

    private record Row(UUID id, String email, String passwordHash, String role, String status, String registrationMethod, boolean mfaEnabled) {
    }
}
