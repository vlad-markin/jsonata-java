package compat.bench;

import java.io.FileReader;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;

/**
 * JVM side of the benchmark (single thread, custom harness, no JMH).
 *
 * <pre>
 * java -Xmx4g -Xms4g -cp target/classes compat.bench.Bench --queries q.json --data model.json
 *      --size N --warmup W --iters I --out results.jsonl [--dump dir] [--only a,b] [--mode m]
 * </pre>
 * Modes (overhead experiments):
 * <ul>
 * <li>plain      - expr.evaluate(data)</li>
 * <li>frame      - expr.evaluate(data, expr.createFrame()) (fresh empty bindings frame per call)</li>
 * <li>novalidate - plain, but setValidateInput(false)</li>
 * <li>callbacks  - fresh frame with no-op entry and exit callbacks</li>
 * <li>bounds     - fresh frame with setRuntimeBounds(60000, 100000)</li>
 * <li>guardrails - compiled with Options timeout=60000, stack=100000, sequence=100000000</li>
 * </ul>
 */
public class Bench {

    static Map<String, String> args(String[] a) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < a.length; i += 2) m.put(a[i].replaceFirst("^--", ""), a[i + 1]);
        return m;
    }

    static double median(double[] s) {
        int n = s.length;
        return n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2;
    }

    static double pct(double[] s, double p) {
        return s[Math.min(s.length - 1, (int) Math.ceil(p * s.length) - 1)];
    }

    static long heapPeakAndReset(List<MemoryPoolMXBean> pools, boolean reset) {
        long sum = 0;
        for (MemoryPoolMXBean p : pools) {
            sum += p.getPeakUsage().getUsed();
            if (reset) p.resetPeakUsage();
        }
        return sum;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] argv) throws Exception {
        Map<String, String> a = args(argv);
        List<Object> queries = (List<Object>) compat.diff.Json.parse(Files.readString(Path.of(a.get("queries"))));
        int size = Integer.parseInt(a.get("size"));
        int warmup = Integer.parseInt(a.getOrDefault("warmup", "5"));
        int iters = Integer.parseInt(a.getOrDefault("iters", "20"));
        String mode = a.getOrDefault("mode", "plain");
        double budgetMs = Double.parseDouble(a.getOrDefault("budget-ms", "20000"));
        double warmBudgetMs = Double.parseDouble(a.getOrDefault("warm-budget-ms", "15000"));
        int minIters = Integer.parseInt(a.getOrDefault("min-iters", "3"));
        Set<String> only = a.containsKey("only") ? new HashSet<>(Arrays.asList(a.get("only").split(","))) : null;
        Path out = Path.of(a.get("out"));

        // com.sun.management.ThreadMXBean via reflection: maven compiles the engine's module-info.java,
        // which does not read jdk.management (we run on the classpath, where this works).
        Object tmx = ManagementFactory.getThreadMXBean();
        java.lang.reflect.Method allocM = Class.forName("com.sun.management.ThreadMXBean")
                .getMethod("getCurrentThreadAllocatedBytes");
        List<MemoryPoolMXBean> heapPools = new ArrayList<>();
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
            if (p.getType() == MemoryType.HEAP) heapPools.add(p);

        long t0 = System.nanoTime();
        Object data;
        try (Reader r = new FileReader(a.get("data"))) {
            data = com.dashjoin.jsonata.json.Json.parseJson(r);
        }
        double loadMs = (System.nanoTime() - t0) / 1e6;
        System.gc();
        long baseHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.err.printf("[jvm] size=%d mode=%s load=%.0fms dataHeap=%dMB maxHeap=%dMB%n", size, mode, loadMs,
                baseHeap >> 20, Runtime.getRuntime().maxMemory() >> 20);

        for (Object qo : queries) {
            Map<String, Object> q = (Map<String, Object>) qo;
            String name = (String) q.get("name");
            String exprStr = (String) q.get("expr");
            if (q.containsKey("sizes")) {
                boolean ok = false;
                for (Object s : (List<Object>) q.get("sizes")) if (((Number) s).intValue() == size) ok = true;
                if (!ok) continue;
            }
            if (only != null && !only.contains(name)) continue;

            Jsonata.Options opts = mode.equals("guardrails")
                    ? Jsonata.Options.builder().timeout(60000L).stack(100000).sequence(100_000_000).build()
                    : null;
            // compile time: median of 200
            double[] ct = new double[200];
            Jsonata expr = null;
            for (int i = 0; i < ct.length; i++) {
                long s = System.nanoTime();
                expr = opts == null ? Jsonata.jsonata(exprStr) : Jsonata.jsonata(exprStr, opts);
                ct[i] = (System.nanoTime() - s) / 1e6;
            }
            Arrays.sort(ct);
            if (mode.equals("novalidate")) expr.setValidateInput(false);

            Object result = null;
            String error = null;
            List<Double> timesL = new ArrayList<>(), allocL = new ArrayList<>();
            int warmDone = 0;
            double warmSpent = 0, measSpent = 0;
            System.gc();
            heapPeakAndReset(heapPools, true);
            try {
                for (int i = 0; ; i++) {
                    boolean measuring = warmDone >= warmup || (warmDone >= 1 && warmSpent > warmBudgetMs);
                    if (measuring && (timesL.size() >= iters || (timesL.size() >= minIters && measSpent > budgetMs))) break;
                    System.gc();
                    Frame f = null;
                    switch (mode) {
                        case "frame": f = expr.createFrame(); break;
                        case "callbacks":
                            f = expr.createFrame();
                            f.setEvaluateEntryCallback((e, in, env) -> { });
                            f.setEvaluateExitCallback((e, in, env, res) -> { });
                            break;
                        case "bounds": f = expr.createFrame(); f.setRuntimeBounds(60000, 100000); break;
                        default: break;
                    }
                    long al0 = (long) allocM.invoke(tmx);
                    long s = System.nanoTime();
                    Object r = f == null ? expr.evaluate(data) : expr.evaluate(data, f);
                    long e = System.nanoTime() - s;
                    long al1 = (long) allocM.invoke(tmx);
                    if (i == 0) result = r;
                    if (!measuring) { warmDone++; warmSpent += e / 1e6; }
                    else { measSpent += e / 1e6; timesL.add(e / 1e6); allocL.add((al1 - al0) / 1048576.0); }
                }
            } catch (Throwable ex) {
                error = ex.getClass().getSimpleName() + " " + ex.getMessage();
                timesL.clear();
                allocL.clear();
            }
            double[] times = timesL.stream().mapToDouble(Double::doubleValue).toArray();
            double[] allocMb = allocL.stream().mapToDouble(Double::doubleValue).toArray();
            long peak = heapPeakAndReset(heapPools, false);
            if (a.containsKey("dump")) {
                Files.createDirectories(Path.of(a.get("dump")));
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("__error", error);
                Files.writeString(Path.of(a.get("dump"), name + "-" + size + ".jvm.json"),
                        compat.diff.Json.write(error != null ? err : result));
            }
            double[] ts = times.clone();
            Arrays.sort(ts);
            double[] as = allocMb.clone();
            Arrays.sort(as);
            StringBuilder sb = new StringBuilder();
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("engine", "jsonata-java(fork)");
            rec.put("jvm", System.getProperty("java.vm.version"));
            rec.put("mode", mode);
            rec.put("query", name);
            rec.put("size", size);
            rec.put("warmup_done", warmDone);
            rec.put("iters_done", times.length);
            rec.put("compile_ms_median", median(ct));
            rec.put("error", error);
            rec.put("eval_ms_median", ts.length > 0 ? median(ts) : null);
            rec.put("eval_ms_p95", ts.length > 0 ? pct(ts, 0.95) : null);
            rec.put("eval_ms_min", ts.length > 0 ? ts[0] : null);
            rec.put("eval_ms_max", ts.length > 0 ? ts[ts.length - 1] : null);
            rec.put("alloc_mb_median", as.length > 0 ? median(as) : null);
            rec.put("heap_peak_mb", peak / 1048576.0);
            rec.put("data_heap_mb", baseHeap / 1048576.0);
            List<Object> tl = new ArrayList<>();
            for (double d : times) tl.add(d);
            rec.put("times_ms", tl);
            sb.append(compat.diff.Json.write(rec)).append('\n');
            Files.writeString(out, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.err.printf("[jvm] %-18s n=%d mode=%s median=%.2fms p95=%.2fms alloc=%.1fMB peak=%.0fMB%s%n", name, size,
                    mode, rec.get("eval_ms_median") == null ? -1.0 : (Double) rec.get("eval_ms_median"),
                    rec.get("eval_ms_p95") == null ? -1.0 : (Double) rec.get("eval_ms_p95"),
                    as.length > 0 ? median(as) : -1.0, peak / 1048576.0, error != null ? " ERROR " + error : "");
        }
    }

}
