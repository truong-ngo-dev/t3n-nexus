package vn.t3nexus.bench.load;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bước 5 của feature 08: trần ghi một dòng mỗi giao dịch của Postgres. Bảng {@code bench_user} có cùng cột, ràng buộc và chỉ mục
 * như {@code user_accounts} của Xác thực (khóa UUID v7, email duy nhất). Mỗi luồng một kết nối, tự xác nhận (autocommit), nên mỗi
 * INSERT là một giao dịch có {@code fsync}. Một lần chạy = một số kết nối; bảng được tạo lại trước khi làm nóng.
 *
 * <p>Tham số: {@code conns=16 warmup=10 measure=30 url=jdbc:postgresql://localhost:5440/bench_db}.
 * Kết quả một dòng: {@code conns rows_per_sec p50_ms p95_ms p99_ms errors rows_total}.
 */
public class JdbcWriteBench {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_HASH = "$2a$10$" + "x".repeat(53);

    public static void main(String[] args) throws Exception {
        String url = arg(args, "url", "jdbc:postgresql://localhost:5440/bench_db");
        int conns = Integer.parseInt(arg(args, "conns", "16"));
        int warmup = Integer.parseInt(arg(args, "warmup", "10"));
        int measure = Integer.parseInt(arg(args, "measure", "30"));

        try (Connection admin = DriverManager.getConnection(url, "bench", "bench"); Statement st = admin.createStatement()) {
            st.execute("DROP TABLE IF EXISTS bench_user");
            st.execute("""
                    CREATE TABLE bench_user (
                        id UUID NOT NULL,
                        email VARCHAR(254) NOT NULL,
                        password_hash VARCHAR(255),
                        role VARCHAR(20) NOT NULL,
                        registration_method VARCHAR(20) NOT NULL,
                        status VARCHAR(10) NOT NULL,
                        mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMPTZ NOT NULL,
                        updated_at TIMESTAMPTZ NOT NULL,
                        CONSTRAINT pk_bench_user PRIMARY KEY (id),
                        CONSTRAINT uq_bench_user_email UNIQUE (email),
                        CONSTRAINT chk_bench_user_email_norm CHECK (email = lower(btrim(email))),
                        CONSTRAINT chk_bench_user_role CHECK (role IN ('USER', 'ADMIN')),
                        CONSTRAINT chk_bench_user_reg CHECK (registration_method IN ('CREDENTIAL', 'OAUTH')),
                        CONSTRAINT chk_bench_user_status CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED')),
                        CONSTRAINT chk_bench_user_pwd CHECK (registration_method <> 'CREDENTIAL' OR password_hash IS NOT NULL)
                    )""");
        }

        var measuring = new AtomicBoolean(false);
        var stop = new AtomicBoolean(false);
        var done = new CountDownLatch(conns);
        var errors = new AtomicLong();
        List<long[]> lats = new ArrayList<>();
        long[] counts = new long[conns];

        for (int i = 0; i < conns; i++) {
            final int idx = i;
            long[] lat = new long[1 << 21];
            lats.add(lat);
            new Thread(() -> {
                int n = 0;
                try (Connection c = DriverManager.getConnection(url, "bench", "bench");
                     PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO bench_user (id, email, password_hash, role, registration_method, status, mfa_enabled, created_at, updated_at)"
                                     + " VALUES (?, ?, ?, 'USER', 'CREDENTIAL', 'ACTIVE', FALSE, ?, ?)")) {
                    c.setAutoCommit(true);
                    while (!stop.get()) {
                        UUID id = uuidV7();
                        Timestamp now = Timestamp.from(Instant.now());
                        ps.setObject(1, id);
                        ps.setString(2, "u" + id + "@bench.test");
                        ps.setString(3, PASSWORD_HASH);
                        ps.setTimestamp(4, now);
                        ps.setTimestamp(5, now);
                        long t0 = System.nanoTime();
                        try {
                            ps.executeUpdate();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                            continue;
                        }
                        long dt = System.nanoTime() - t0;
                        if (measuring.get() && n < lat.length) lat[n++] = dt;
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
                counts[idx] = n;
                done.countDown();
            }).start();
        }

        Thread.sleep(warmup * 1000L);
        long start = System.nanoTime();
        measuring.set(true);
        Thread.sleep(measure * 1000L);
        measuring.set(false);
        long elapsed = System.nanoTime() - start;
        stop.set(true);
        done.await();

        long total = 0;
        for (long c : counts) total += c;
        long[] all = new long[(int) total];
        int p = 0;
        for (int i = 0; i < conns; i++) {
            System.arraycopy(lats.get(i), 0, all, p, (int) counts[i]);
            p += (int) counts[i];
        }
        Arrays.sort(all);
        System.out.printf(java.util.Locale.ROOT, "%d %.0f %.2f %.2f %.2f %d %d%n", conns, total / (elapsed / 1e9),
                all[(int) (all.length * 0.50)] / 1e6, all[(int) (all.length * 0.95)] / 1e6,
                all[Math.min(all.length - 1, (int) (all.length * 0.99))] / 1e6, errors.get(), total);
    }

    /** UUID v7 (ADR-0001): 48 bit mili giây + ngẫu nhiên, sắp xếp theo thời gian. */
    private static UUID uuidV7() {
        long ms = System.currentTimeMillis();
        long rnd = RANDOM.nextLong();
        long msb = (ms << 16) | 0x7000L | ((rnd >>> 52) & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    private static String arg(String[] args, String key, String def) {
        for (String a : args) {
            if (a.startsWith(key + "=")) return a.substring(key.length() + 1);
        }
        return def;
    }
}
