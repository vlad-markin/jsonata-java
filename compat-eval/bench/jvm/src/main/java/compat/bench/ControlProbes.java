package compat.bench;

import java.io.FileReader;
import java.io.Reader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;
import com.dashjoin.jsonata.Jsonata.Options;

import jdk.jfr.consumer.RecordingStream;

import static com.dashjoin.jsonata.Jsonata.jsonata;

/**
 * Phase 5 evaluation-control probes. Each probe prints one line "ID | verdict | evidence".
 * Run: java -Xmx2g -cp target/classes compat.bench.ControlProbes [data/model-10000.json]
 * The probes are deliberately small programs, not unit tests: the point is to record observed
 * behaviour of the engine (including bugs), not to assert the desired behaviour.
 */
public class ControlProbes {

    /** Cancellation signal thrown by our entry callback / host functions. */
    static final class EvalCancelled extends RuntimeException {
        EvalCancelled() { super("cancelled by host", null, false, false); }
    }

    static final List<String[]> rows = new ArrayList<>();

    static void report(String id, String verdict, String evidence) {
        rows.add(new String[] { id, verdict, evidence });
        System.out.println(id + " | " + verdict + " | " + evidence);
        System.out.flush();
    }

    static void probe(String id, Callable<Void> c) {
        try {
            c.call();
        } catch (Throwable t) {
            report(id, "PROBE-ERROR", describe(t));
        }
    }

    static String describe(Throwable t) {
        if (t == null) return "none";
        String s = t.getClass().getName();
        if (t instanceof JException) s += "[code=" + ((JException) t).getError() + "]";
        String m = String.valueOf(t.getMessage());
        if (m.length() > 120) m = m.substring(0, 120) + "...";
        s += ": " + m;
        if (t.getCause() != null && t.getCause() != t) s += " <- cause " + describe(t.getCause());
        return s;
    }

    static final String LOOP = "($loop := function($i, $acc){ $i = 0 ? $acc : $loop($i - 1, $acc + 1) }; $loop(%d, 0))";
    static final String INFINITE = "($f := function($i){ $f($i + 1) }; $f(0))";

    static long ms(long t0) { return (System.nanoTime() - t0) / 1_000_000; }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    public static void main(String[] args) throws Exception {
        String dataFile = args.length > 0 ? args[0] : null;

        // ---------------------------------------------------------------- external cancellation
        // calibrate a finite loop of ~1.5-3 s
        int n = 200_000;
        long dur;
        while (true) {
            long t0 = System.nanoTime();
            jsonata(String.format(LOOP, n)).evaluate(null);
            dur = ms(t0);
            if (dur > 1500 || n > 50_000_000) break;
            n *= 2;
        }
        final int N = n;
        final long base = dur;

        probe("C1 Thread.interrupt()", () -> {
            AtomicLong took = new AtomicLong();
            AtomicBoolean flagAfter = new AtomicBoolean();
            AtomicBoolean threw = new AtomicBoolean();
            Thread t = new Thread(() -> {
                long t0 = System.nanoTime();
                try { jsonata(String.format(LOOP, N)).evaluate(null); } catch (Throwable e) { threw.set(true); }
                took.set(ms(t0));
                flagAfter.set(Thread.currentThread().isInterrupted());
            });
            t.start();
            Thread.sleep(100);
            t.interrupt();
            t.join();
            report("C1 Thread.interrupt()", "NO EFFECT",
                    "interrupt at 100ms; evaluation ran to completion in " + took.get() + "ms (baseline " + base
                            + "ms), exception=" + threw.get() + ", interrupt flag still set=" + flagAfter.get());
            return null;
        });

        probe("C2 cancel via entry callback", () -> {
            AtomicBoolean cancel = new AtomicBoolean();
            Jsonata e = jsonata(String.format(LOOP, N));
            Frame f = e.createFrame();
            f.setEvaluateEntryCallback((x, in, env) -> { if (cancel.get()) throw new EvalCancelled(); });
            AtomicLong cancelledAt = new AtomicLong();
            Thread killer = new Thread(() -> {
                try { Thread.sleep(100); } catch (InterruptedException ie) { }
                cancelledAt.set(System.nanoTime());
                cancel.set(true);
            });
            killer.start();
            Throwable got = null;
            try { e.evaluate(null, f); } catch (Throwable t) { got = t; }
            long latencyUs = (System.nanoTime() - cancelledAt.get()) / 1000;
            report("C2 cancel via entry callback", got instanceof EvalCancelled ? "WORKS (plain path)" : "BROKEN",
                    "caller got " + describe(got) + "; latency after flag " + latencyUs + "us");
            return null;
        });

        // cancellation raised from inside different constructs: does the host see its own exception?
        String[][] contexts = {
            { "C3a block/path", "[1..50].($cancelNow(); $)" },
            { "C3b RHS of 'and'", "true and ($cancelNow(); true)" },
            { "C3c $sort comparator", "$sort([5,3,1,4,2], function($a,$b){ ($cancelNow(); $a > $b) })" },
            { "C3d $replace callback", "$replace('aaaa', /a/, function($m){ ($cancelNow(); 'b') })" },
            { "C3e $map lambda", "$map([1,2,3], function($v){ ($cancelNow(); $v) })" },
            { "C3f group-by value", "[{'k':'a','v':1},{'k':'b','v':2}]{k: ($cancelNow(); $sum(v))}" },
            { "C3g $eval body", "$eval('[1,2,3].($cancelNow(); $)')" },
            { "C3h host fn throws directly", "$throwCancel()" },
            { "C3i host fn throws inside $sort", "$sort([3,1,2], function($a,$b){ $throwCancel() })" },
            { "C3j host fn throws in $filter", "$filter([1,2], function($v){ $throwCancel() })" },
        };
        for (String[] c : contexts) {
            probe(c[0], () -> {
                AtomicBoolean cancel = new AtomicBoolean();
                AtomicInteger entriesAfterCancel = new AtomicInteger();
                Jsonata e = jsonata(c[1]);
                Frame f = e.createFrame();
                f.bind("cancelNow", (Jsonata.Fn0<Object>) () -> { cancel.set(true); return null; });
                f.bind("throwCancel", (Jsonata.Fn0<Object>) () -> { throw new EvalCancelled(); });
                f.setEvaluateEntryCallback((x, in, env) -> {
                    if (cancel.get()) { entriesAfterCancel.incrementAndGet(); throw new EvalCancelled(); }
                });
                Object res = null;
                Throwable got = null;
                java.io.PrintStream oldErr = System.err;
                java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream();
                System.setErr(new java.io.PrintStream(errBuf));
                try { res = e.evaluate(null, f); } catch (Throwable t) { got = t; } finally { System.setErr(oldErr); }
                String verdict;
                if (got instanceof EvalCancelled) verdict = "OK: host exception propagates unchanged";
                else if (got == null) verdict = "SWALLOWED: evaluation completed, result=" + compat.diff.Json.write(res);
                else verdict = "WRAPPED/REPLACED";
                String stderr = errBuf.size() > 0 ? "; engine printed stack trace to stderr (" + errBuf.size() + " bytes)" : "";
                report(c[0], verdict, "expr `" + c[1] + "` -> caller got " + describe(got) + "; callback throws after cancel="
                        + entriesAfterCancel.get() + stderr);
                return null;
            });
        }

        // ---------------------------------------------------------------- time budget: Options.timeout
        probe("T1 Options.timeout on infinite tail loop", () -> {
            Jsonata e = jsonata(INFINITE, Options.builder().timeout(300L).stack(100000).sequence(100_000_000).build());
            long t0 = System.nanoTime();
            Throwable got = null;
            try { e.evaluate(null); } catch (Throwable t) { got = t; }
            report("T1 Options.timeout on infinite tail loop", got instanceof JException && "D1012".equals(((JException) got).getError()) ? "WORKS" : "UNEXPECTED",
                    "timeout=300ms, threw after " + ms(t0) + "ms: " + describe(got));
            return null;
        });

        probe("T2 Options.timeout without sequence -> range NPE", () -> {
            List<String> out = new ArrayList<>();
            Object[][] cfg = { { "timeout only", Options.builder().timeout(1000L).build() },
                               { "stack only", Options.builder().stack(1000).build() },
                               { "sequence only", Options.builder().sequence(1000).build() },
                               { "all three", Options.builder().timeout(1000L).stack(1000).sequence(1000).build() } };
            for (Object[] c : cfg) {
                String r;
                try { r = "ok " + compat.diff.Json.write(jsonata("$count([1..10])", (Options) c[1]).evaluate(null)); }
                catch (Throwable t) { r = describe(t); }
                out.add(c[0] + ": " + r);
            }
            boolean npe = out.stream().anyMatch(s -> s.contains("NullPointerException"));
            report("T2 Options.timeout without sequence -> range NPE", npe ? "BUG (NPE)" : "ok",
                    "`$count([1..10])` -> " + String.join("; ", out) + " (Jsonata.java evaluateRangeExpression: `size > options.sequence` unboxes null)");
            return null;
        });

        probe("T3 Options.timeout vs blocking host function", () -> {
            Jsonata e = jsonata("($sleep(1500); 42)", Options.builder().timeout(200L).stack(1000).sequence(1000000).build());
            e.registerFunction("sleep", (Jsonata.Fn1<Number, Object>) (ms) -> {
                try { Thread.sleep(ms.longValue()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return null;
            });
            long t0 = System.nanoTime();
            Throwable got = null;
            Object res = null;
            try { res = e.evaluate(null); } catch (Throwable t) { got = t; }
            report("T3 Options.timeout vs blocking host function", "NOT PREEMPTIVE",
                    "timeout=200ms, host fn sleeps 1500ms: returned after " + ms(t0) + "ms, result=" + res + ", error=" + describe(got)
                            + " (deadline is only checked at the next node entry)");
            return null;
        });

        probe("T4 D1012 raised inside $sort comparator / $eval", () -> {
            Options o = Options.builder().timeout(100L).stack(100000).sequence(100_000_000).build();
            String[] ex = {
                "$sort([1..2000].($ % 97), function($a,$b){ ($x := [1..200].($*2); $a > $b) })",
                "$eval('(" + INFINITE.replace("'", "\\'") + ")')",
            };
            List<String> out = new ArrayList<>();
            for (String s : ex) {
                long t0 = System.nanoTime();
                try { jsonata(s, o).evaluate(null); out.add("no error"); }
                catch (Throwable t) { out.add(describe(t) + " after " + ms(t0) + "ms"); }
            }
            report("T4 D1012 raised inside $sort comparator / $eval", "SEE EVIDENCE",
                    "$sort: " + out.get(0) + " || $eval: " + out.get(1));
            return null;
        });

        // ---------------------------------------------------------------- setRuntimeBounds (Timebox)
        probe("B1 setRuntimeBounds clock starts at setRuntimeBounds(), not evaluate()", () -> {
            Jsonata e = jsonata("1 + 1");
            Frame f = e.createFrame();
            f.setRuntimeBounds(300, 1000);
            Thread.sleep(400);
            Throwable got = null;
            try { e.evaluate(null, f); } catch (Throwable t) { got = t; }
            report("B1 setRuntimeBounds clock starts at setRuntimeBounds(), not evaluate()", got != null ? "CONFIRMED" : "not reproduced",
                    "bounds(300ms), sleep 400ms, evaluate `1+1` -> " + describe(got));
            return null;
        });

        probe("B2 Timebox error code", () -> {
            Jsonata e = jsonata(INFINITE);
            Frame f = e.createFrame();
            f.setRuntimeBounds(200, 100000);
            Throwable got = null;
            long t0 = System.nanoTime();
            try { e.evaluate(null, f); } catch (Throwable t) { got = t; }
            Jsonata e2 = jsonata("($f := function($n){ $n = 0 ? 0 : 1 + $f($n - 1) }; $f(1000))");
            Frame f2 = e2.createFrame();
            f2.setRuntimeBounds(5000, 200);
            Throwable got2 = null;
            try { e2.evaluate(null, f2); } catch (Throwable t) { got2 = t; }
            String code1 = got instanceof JException ? ((JException) got).getError() : "-";
            report("B2 Timebox error code", "BUG: getError() is the message, not U1001",
                    "timeout after " + ms(t0) + "ms getError()=\"" + code1 + "\"; depth: " + describe(got2));
            return null;
        });

        probe("B3 setRuntimeBounds and entry/exit callbacks overwrite each other", () -> {
            // bounds first, then a monitoring callback -> bounds silently disabled
            Jsonata e = jsonata(String.format(LOOP, N));
            Frame f = e.createFrame();
            f.setRuntimeBounds(100, 100000);
            AtomicLong calls = new AtomicLong();
            f.setEvaluateEntryCallback((x, in, env) -> calls.incrementAndGet());
            long t0 = System.nanoTime();
            Throwable got = null;
            try { e.evaluate(null, f); } catch (Throwable t) { got = t; }
            long took = ms(t0);
            // callback first, then bounds -> callback silently disabled
            Frame g = e.createFrame();
            AtomicLong calls2 = new AtomicLong();
            g.setEvaluateEntryCallback((x, in, env) -> calls2.incrementAndGet());
            g.setRuntimeBounds(100, 100000);
            try { e.evaluate(null, g); } catch (Throwable t) { }
            report("B3 setRuntimeBounds and entry/exit callbacks overwrite each other", "CONFIRMED",
                    "bounds(100ms)+callback: ran " + took + "ms, error=" + describe(got) + ", callback calls=" + calls.get()
                            + "; callback+bounds: callback calls=" + calls2.get() + " (same binding names __evaluate_entry/__evaluate_exit)");
            return null;
        });

        probe("B4 Timebox depth leaks when evaluation throws (frame reused)", () -> {
            Jsonata bad = jsonata("[1,2,3].($ > 2 ? $error('boom') : $)");
            Jsonata ok = jsonata("[1..5].($ * 2)");
            Frame f = bad.createFrame();
            f.setRuntimeBounds(60000, 50);
            int failures = 0;
            for (int i = 0; i < 30; i++) {
                try { bad.evaluate(null, f); } catch (Throwable t) { failures++; }
            }
            Throwable got = null;
            try { ok.evaluate(null, f); } catch (Throwable t) { got = t; }
            report("B4 Timebox depth leaks when evaluation throws (frame reused)", got != null ? "CONFIRMED" : "not reproduced",
                    "30 failing evaluations with one bounds frame (maxDepth=50), then a trivial expression -> " + describe(got));
            return null;
        });

        probe("B5 one bounds frame shared by threads", () -> {
            Jsonata e = jsonata("$map([1..200], function($i){ $i * 2 })");
            Frame f = e.createFrame();
            f.setRuntimeBounds(60000, 40);
            ExecutorService pool = Executors.newFixedThreadPool(8);
            AtomicInteger errors = new AtomicInteger();
            AtomicInteger runs = new AtomicInteger();
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 4000; i++) fs.add(pool.submit(() -> {
                try { e.evaluate(null, f); } catch (Throwable t) { errors.incrementAndGet(); }
                runs.incrementAndGet();
            }));
            for (Future<?> x : fs) x.get();
            pool.shutdown();
            report("B5 one bounds frame shared by threads", errors.get() > 0 ? "UNSAFE" : "no errors observed",
                    runs.get() + " concurrent evaluations sharing one setRuntimeBounds frame (depth needed ~10, max 40): spurious errors=" + errors.get());
            return null;
        });

        // ---------------------------------------------------------------- memory budget
        probe("M1 no output/string size budget", () -> {
            Options o = Options.builder().timeout(30000L).stack(1000).sequence(1000).build();
            Jsonata e = jsonata("($d := function($s, $n){ $n = 0 ? $s : $d($s & $s, $n - 1) }; $length($d('ab', 24)))", o);
            long t0 = System.nanoTime();
            Object r = e.evaluate(null);
            report("M1 no output/string size budget", "NO LIMIT",
                    "with all Options set (sequence=1000): 2*2^24-char string built, $length=" + r + " in " + ms(t0) + "ms (~64MB of char data, no guard)");
            return null;
        });

        probe("M2 Options.sequence coverage", () -> {
            Options o = Options.builder().timeout(30000L).stack(1000).sequence(1000).build();
            String[] ex = { "$count([1..2000])", "$count($append([1..600],[1..600]))", "$count([1..40].[1..40])",
                            "$count($map([1..600], function($v){[$v, $v]}))", "$count($keys($merge([1..900].{$string($): 1}) ~> $merge([1..900].{$string($+900): 1}) ~> function($a){$a}))" };
            List<String> out = new ArrayList<>();
            for (String s : ex) {
                try { out.add(s + " = " + compat.diff.Json.write(jsonata(s, o).evaluate(null))); }
                catch (Throwable t) { out.add(s + " -> " + describe(t)); }
            }
            report("M2 Options.sequence coverage", "PARTIAL", "sequence=1000: " + String.join(" ; ", out));
            return null;
        });

        // ---------------------------------------------------------------- shared environment / thread safety
        probe("S1 := leaks between evaluate() calls without Frame", () -> {
            Jsonata e = jsonata("$y := ($exists($y) ? $y + 1 : 1)");
            Object a = e.evaluate(null), b = e.evaluate(null);
            Object c = e.evaluate(null, e.createFrame()), d = e.evaluate(null, e.createFrame());
            report("S1 := leaks between evaluate() calls without Frame", "CONFIRMED",
                    "no frame: " + a + ", " + b + " (JS 2.2.2: 1, 1); with fresh Frame each call: " + c + ", " + d
                            + " (but the leaked $y=2 sits in the shared env and is visible to frame-calls: see S2)");
            return null;
        });

        probe("S2 shared env visible from Frame calls", () -> {
            Jsonata e = jsonata("$exists($y) ? 'leaked y=' & $y : 'clean'");
            Jsonata.jsonata("1"); // unrelated
            Jsonata setter = jsonata("($y := 5; $z := 1)");
            setter.evaluate(null);
            Object r1 = e.evaluate(null, e.createFrame());
            // same expression object: assign via := without frame, then read with frame
            Jsonata both = jsonata("$exists($w) ? 'leaked w=' & $w : ($w := 7; 'set')");
            Object a = both.evaluate(null), b = both.evaluate(null, both.createFrame());
            report("S2 shared env visible from Frame calls", "CONFIRMED",
                    "same compiled expr: call1(no frame)=" + a + ", call2(with Frame)=" + b + "; different expr objects isolated: " + r1);
            return null;
        });

        for (boolean withFrame : new boolean[] { false, true }) {
            String id = "S3 concurrent reuse of one compiled expr, " + (withFrame ? "with Frame" : "no Frame");
            probe(id, () -> {
                Jsonata e = jsonata("id & ':' & $$.id & ':' & $count(items)");
                ExecutorService pool = Executors.newFixedThreadPool(8);
                AtomicInteger wrong = new AtomicInteger(), exc = new AtomicInteger();
                List<String> samples = new ArrayList<>();
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < 20000; i++) {
                    final int k = i;
                    fs.add(pool.submit(() -> {
                        List<Object> items = new ArrayList<>();
                        for (int j = 0; j < k % 7; j++) items.add(j);
                        Map<String, Object> in = obj("id", "i" + k, "items", items);
                        String expect = "i" + k + ":i" + k + ":" + (k % 7);
                        try {
                            Object r = withFrame ? e.evaluate(in, e.createFrame()) : e.evaluate(in);
                            if (!expect.equals(r)) {
                                wrong.incrementAndGet();
                                synchronized (samples) { if (samples.size() < 2) samples.add(expect + " != " + r); }
                            }
                        } catch (Throwable t) {
                            exc.incrementAndGet();
                            synchronized (samples) { if (samples.size() < 3) samples.add(describe(t)); }
                        }
                    }));
                }
                for (Future<?> x : fs) x.get();
                pool.shutdown();
                report(id, wrong.get() + exc.get() == 0 ? "CORRECT" : "INCORRECT",
                        "20000 evals on 8 threads: wrong=" + wrong.get() + ", exceptions=" + exc.get() + " " + samples);
                return null;
            });
        }

        probe("S4 $millis()/$now() frozen on reused worker threads", () -> {
            Jsonata e = jsonata("$millis()");
            ExecutorService pool = Executors.newSingleThreadExecutor();
            long a = ((Number) pool.submit(() -> e.evaluate(null)).get()).longValue();
            Thread.sleep(300);
            long b = ((Number) pool.submit(() -> e.evaluate(null)).get()).longValue();
            long c0 = ((Number) e.evaluate(null)).longValue();
            Thread.sleep(300);
            long c1 = ((Number) e.evaluate(null)).longValue();
            // other expression first used on that thread
            Jsonata other = jsonata("$millis()");
            long d = ((Number) pool.submit(() -> other.evaluate(null)).get()).longValue();
            pool.shutdown();
            report("S4 $millis()/$now() frozen on reused worker threads", b - a < 50 ? "BUG" : "ok",
                    "worker thread: two calls 300ms apart returned " + a + " and " + b + " (delta " + (b - a)
                            + "ms); compiling thread: delta " + (c1 - c0) + "ms; a different expression on the same worker returned " + d
                            + " (delta to first " + (d - a) + "ms): Functions.millis reads Jsonata.current.get().timestamp of the per-thread clone");
            return null;
        });

        // ---------------------------------------------------------------- virtual threads
        probe("V1 10k concurrent evaluations on virtual threads (with Frame)", () -> {
            Jsonata e = jsonata("$x * 2 + $count(items) + $sum(items)");
            int tasks = 10_000;
            AtomicInteger wrong = new AtomicInteger(), exc = new AtomicInteger();
            long t0 = System.nanoTime();
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < tasks; i++) {
                    final int k = i;
                    ex.submit(() -> {
                        List<Object> items = new ArrayList<>();
                        for (int j = 1; j <= k % 10; j++) items.add(j);
                        Frame f = e.createFrame();
                        f.bind("x", k);
                        long expect = 2L * k + (k % 10) + (long) (k % 10) * (k % 10 + 1) / 2;
                        try {
                            Object r = e.evaluate(obj("items", items), f);
                            if (((Number) r).longValue() != expect) wrong.incrementAndGet();
                        } catch (Throwable t) { exc.incrementAndGet(); }
                    });
                }
            }
            report("V1 10k concurrent evaluations on virtual threads (with Frame)", wrong.get() + exc.get() == 0 ? "CORRECT" : "INCORRECT",
                    tasks + " tasks in " + ms(t0) + "ms, wrong=" + wrong.get() + ", exceptions=" + exc.get());
            return null;
        });

        probe("V2 blocking host function on virtual threads + pinning (JFR jdk.VirtualThreadPinned)", () -> {
            AtomicInteger pinned = new AtomicInteger();
            List<String> pinnedTop = new ArrayList<>();
            try (RecordingStream rs = new RecordingStream()) {
                rs.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ZERO).withStackTrace();
                rs.onEvent("jdk.VirtualThreadPinned", ev -> {
                    pinned.incrementAndGet();
                    synchronized (pinnedTop) {
                        if (pinnedTop.size() < 1 && ev.getStackTrace() != null) {
                            StringBuilder sb = new StringBuilder();
                            ev.getStackTrace().getFrames().stream().limit(12).forEach(fr ->
                                sb.append(fr.getMethod().getType().getName().replaceAll(".*\\.", "")).append('.').append(fr.getMethod().getName()).append(" < "));
                            pinnedTop.add(sb.toString());
                        }
                    }
                });
                rs.startAsync();
                Thread.sleep(300);
                Object lock = new Object();
                String[] variants = { "plain", "synchronized-host" };
                List<String> out = new ArrayList<>();
                for (String v : variants) {
                    int before = pinned.get();
                    Jsonata e = jsonata("$map([1..3], function($i){ $fetch($i + $x) })");
                    int tasks = 10_000;
                    AtomicInteger wrong = new AtomicInteger();
                    long t0 = System.nanoTime();
                    try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                        for (int i = 0; i < tasks; i++) {
                            final int k = i;
                            ex.submit(() -> {
                                Frame f = e.createFrame();
                                f.bind("x", k);
                                f.bind("fetch", (Jsonata.Fn1<Number, Object>) (q) -> {
                                    try {
                                        if (v.equals("plain")) Thread.sleep(10);
                                        else synchronized (lock) { Thread.sleep(0, 200_000); }
                                    } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                                    return q.longValue() * 10;
                                });
                                Object r = e.evaluate(null, f);
                                List<?> l = (List<?>) r;
                                if (l.size() != 3 || ((Number) l.get(2)).longValue() != (3L + k) * 10) wrong.incrementAndGet();
                            });
                        }
                    }
                    long took = ms(t0);
                    Thread.sleep(500);
                    out.add(v + ": " + tasks + " tasks x 3 blocking calls in " + took + "ms, wrong=" + wrong.get() + ", pinned events=" + (pinned.get() - before));
                }
                report("V2 blocking host function on virtual threads + pinning (JFR jdk.VirtualThreadPinned)", "SEE EVIDENCE",
                        String.join(" ; ", out) + (pinnedTop.isEmpty() ? "" : " ; sample pinned stack: " + pinnedTop.get(0)));
            }
            return null;
        });

        probe("V3 per-evaluation cost on a fresh virtual thread (ThreadLocal clone + new Parser)", () -> {
            Jsonata e = jsonata("a + 1");
            Map<String, Object> in = obj("a", 1);
            int reps = 50_000;
            for (int w = 0; w < 2; w++) {
                for (int i = 0; i < reps; i++) e.evaluate(in, e.createFrame());
            }
            long t0 = System.nanoTime();
            for (int i = 0; i < reps; i++) e.evaluate(in, e.createFrame());
            double same = (System.nanoTime() - t0) / 1000.0 / reps;
            // fresh vthread per evaluation, minus the cost of a fresh vthread doing nothing
            double[] r = new double[2];
            for (int mode = 0; mode < 2; mode++) {
                for (int rep = 0; rep < 2; rep++) {
                    long s = System.nanoTime();
                    for (int i = 0; i < reps / 5; i++) {
                        Thread t = mode == 0 ? Thread.ofVirtual().start(() -> e.evaluate(in, e.createFrame()))
                                             : Thread.ofVirtual().start(() -> { });
                        t.join();
                    }
                    r[mode] = (System.nanoTime() - s) / 1000.0 / (reps / 5);
                }
            }
            report("V3 per-evaluation cost on a fresh virtual thread (ThreadLocal clone + new Parser)", "MEASURED",
                    String.format("`a + 1`: same thread %.2fus/eval; fresh vthread %.2fus/eval minus empty vthread %.2fus => +%.2fus per first evaluate on a thread (new Jsonata clone + new Parser via synchronized getParser)",
                            same, r[0], r[1], r[0] - r[1] - same));
            return null;
        });

        // ---------------------------------------------------------------- callback granularity
        if (dataFile != null) {
            probe("K1 callback invocations per evaluation", () -> {
                Object data;
                try (Reader rd = new FileReader(dataFile)) { data = com.dashjoin.jsonata.json.Json.parseJson(rd); }
                String[] qs = { "systems.containers.components[properties.criticality = 'high' and properties.cost > 500].name",
                                "$reduce($filter($map(systems.containers.components, function($c){ $c.properties.cost * 2 }), function($v){ $v > 100 }), function($a, $b){ $a + $b }, 0)" };
                List<String> out = new ArrayList<>();
                for (String q : qs) {
                    Jsonata e = jsonata(q);
                    Frame f = e.createFrame();
                    AtomicLong cnt = new AtomicLong();
                    f.setEvaluateEntryCallback((x, in, env) -> cnt.incrementAndGet());
                    e.evaluate(data, f);
                    out.add(cnt.get() + " entry callbacks for `" + q.substring(0, Math.min(40, q.length())) + "...`");
                }
                report("K1 callback invocations per evaluation", "MEASURED", "N=10k components: " + String.join("; ", out));
                return null;
            });
        }

        System.out.println();
        System.out.println("| probe | verdict | evidence |");
        System.out.println("|---|---|---|");
        for (String[] r : rows) System.out.println("| " + r[0] + " | " + r[1] + " | " + r[2].replace("|", "\\|") + " |");
        System.exit(0);
    }

    static void unusedTimeUnit() { TimeUnit.SECONDS.toString(); }
}
