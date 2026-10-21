package vn.t3nexus.bench.load;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bước 3 của feature 08: trần băm mật khẩu/giây của máy, cùng {@link BCryptPasswordEncoder} như {@code oauth2-service}.
 * Không HTTP, không cơ sở dữ liệu: chỉ CPU. Mỗi (hệ số, số luồng) có giai đoạn làm nóng bỏ qua, rồi giai đoạn đo.
 *
 * <p>Tham số: {@code costs=10,11,12 threads=1,2,4,8,16 warmup=5 measure=20 repeats=3}.
 * Mỗi dòng kết quả: {@code cost threads run hashes_per_sec p50_ms p99_ms}.
 */
public class BcryptBench {

    private static final String PASSWORD = "Passw0rdE2e";

    public static void main(String[] args) throws Exception {
        int[] costs = ints(arg(args, "costs", "10,11,12"));
        int[] threads = ints(arg(args, "threads", "1,2,4,8,16"));
        int warmup = Integer.parseInt(arg(args, "warmup", "5"));
        int measure = Integer.parseInt(arg(args, "measure", "20"));
        int repeats = Integer.parseInt(arg(args, "repeats", "3"));

        System.out.printf("# jdk=%s cores=%d%n", System.getProperty("java.version"), Runtime.getRuntime().availableProcessors());
        System.out.println("cost threads run hashes_per_sec p50_ms p99_ms");
        for (int cost : costs) {
            var encoder = new BCryptPasswordEncoder(cost);
            for (int t : threads) {
                for (int run = 1; run <= repeats; run++) {
                    runOnce(encoder, t, warmup, measure, cost, run);
                }
            }
        }
    }

    private static void runOnce(BCryptPasswordEncoder encoder, int threads, int warmupSec, int measureSec, int cost, int run)
            throws Exception {
        var measuring = new AtomicBoolean(false);
        var stop = new AtomicBoolean(false);
        var done = new CountDownLatch(threads);
        List<long[]> perThread = new ArrayList<>();
        List<Thread> pool = new ArrayList<>();
        long[] counts = new long[threads];

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            long[] lat = new long[1 << 20];
            perThread.add(lat);
            var th = new Thread(() -> {
                int n = 0;
                while (!stop.get()) {
                    long t0 = System.nanoTime();
                    encoder.encode(PASSWORD);
                    long dt = System.nanoTime() - t0;
                    if (measuring.get() && n < lat.length) {
                        lat[n++] = dt;
                    }
                }
                counts[idx] = n;
                done.countDown();
            }, "bcrypt-" + i);
            pool.add(th);
            th.start();
        }

        Thread.sleep(warmupSec * 1000L);
        long start = System.nanoTime();
        measuring.set(true);
        Thread.sleep(measureSec * 1000L);
        measuring.set(false);
        long elapsed = System.nanoTime() - start;
        stop.set(true);
        done.await();

        long total = 0;
        for (long c : counts) total += c;
        long[] all = new long[(int) total];
        int p = 0;
        for (int i = 0; i < threads; i++) {
            System.arraycopy(perThread.get(i), 0, all, p, (int) counts[i]);
            p += (int) counts[i];
        }
        Arrays.sort(all);
        double perSec = total / (elapsed / 1e9);
        System.out.printf(java.util.Locale.ROOT, "%d %d %d %.1f %.1f %.1f%n", cost, threads, run, perSec,
                all[(int) (all.length * 0.50)] / 1e6, all[Math.min(all.length - 1, (int) (all.length * 0.99))] / 1e6);
        System.out.flush();
    }

    private static String arg(String[] args, String key, String def) {
        for (String a : args) {
            if (a.startsWith(key + "=")) return a.substring(key.length() + 1);
        }
        return def;
    }

    private static int[] ints(String csv) {
        return Arrays.stream(csv.split(",")).mapToInt(Integer::parseInt).toArray();
    }
}
