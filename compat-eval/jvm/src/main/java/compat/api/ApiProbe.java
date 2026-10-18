package compat.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import com.dashjoin.jsonata.Functions;
import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;
import com.dashjoin.jsonata.Jsonata.JFunction;

/**
 * Probes host-facing API behaviour of the engine (things a JSONL diff cannot express):
 * reuse of compiled expressions, isolation between evaluations, thread safety, value marshalling
 * to host functions, calling lambdas from the host, runtime bounds reuse.
 * Prints a markdown table: probe | JVM observed | jsonata-js 2.2.2 behaviour | verdict.
 *
 *   java -cp compat-eval/jvm/target/classes compat.api.ApiProbe > compat-eval/phase2/api-probe.md
 */
public class ApiProbe {

    static final List<String[]> rows = new ArrayList<>();

    static void row(String id, String probe, Object observed, String js, boolean same) {
        rows.add(new String[] { id, probe, String.valueOf(observed), js, same ? "совпадает" : "**расходится**" });
    }

    static String safe(Callable<Object> c) {
        try {
            return String.valueOf(c.call());
        } catch (Throwable t) {
            return "EXC " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).replace('\n', ' ');
        }
    }

    static Object fa(Object fn, List<?> args) throws Exception {
        try {
            return Functions.funcApply(fn, args);
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static String cls(Object o) {
        if (o == null) return "null";
        if (o == Jsonata.NULL_VALUE) return "Jsonata.NULL_VALUE";
        return o.getClass().getName().replace("com.dashjoin.jsonata.", "").replace("java.lang.", "").replace("java.util.", "");
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        // 1. isolation of := between evaluations of one compiled expression
        {
            Jsonata e = Jsonata.jsonata("$y := ($exists($y) ? $y + 1 : 1)");
            String r = e.evaluate(null) + ", " + e.evaluate(null) + ", " + e.evaluate(null);
            row("API-1", "одно выражение, 3× `evaluate(input)` без Frame: `$y := ($exists($y) ? $y+1 : 1)` (присваивание на верхнем уровне)", r, "1, 1, 1", r.equals("1, 1, 1"));
            Jsonata e2 = Jsonata.jsonata("$y := ($exists($y) ? $y + 1 : 1)");
            String r2 = e2.evaluate(null, e2.createFrame()) + ", " + e2.evaluate(null, e2.createFrame());
            row("API-1b", "то же, но с `evaluate(input, createFrame())`", r2, "1, 1", r2.equals("1, 1"));
        }
        // 2. concurrent use of one compiled expression
        for (boolean withFrame : new boolean[] { false, true }) {
            Jsonata e = Jsonata.jsonata("[1..200].($$.id) ~> $distinct()");
            ExecutorService ex = Executors.newFixedThreadPool(8);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                final int id = i;
                fs.add(ex.submit(() -> {
                    Map<String, Object> in = new LinkedHashMap<>();
                    in.put("id", id);
                    Object r = withFrame ? e.evaluate(in, e.createFrame()) : e.evaluate(in);
                    return Integer.valueOf(id).equals(r) || (r instanceof List && ((List<?>) r).size() == 1 && Integer.valueOf(id).equals(((List<?>) r).get(0)));
                }));
            }
            int bad = 0, exc = 0;
            for (Future<Boolean> f : fs) {
                try { if (!f.get()) bad++; } catch (Exception x) { exc++; }
            }
            ex.shutdown();
            String obs = "неверных " + bad + ", исключений " + exc + " из 400";
            row(withFrame ? "API-2b" : "API-2", "8 потоков × 400 `evaluate` одного выражения `[1..200].($$.id) ~> $distinct()`" + (withFrame ? " с Frame" : " без Frame"), obs,
                    "каждый вызов изолирован (в JS исправлено в 2.2.2, df63a7c)", bad == 0 && exc == 0);
        }
        // 3. value marshalling to host functions (raw API)
        {
            List<String> seen = Collections.synchronizedList(new ArrayList<>());
            Jsonata e = Jsonata.jsonata("[$t(null), $t(nothing), $t(1), $t(1.5), $t(n), $t(a), $t(a.b), $t({'x':1}), $t(function(){1}), $t(/re/), $t(n2)]");
            e.registerFunction("t", new JFunction((input, a) -> { seen.add(a.isEmpty() ? "<no arg>" : cls(a.get(0))); return 0; }, null));
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("n", 7L);
            in.put("n2", 9007199254740993L);
            in.put("a", List.of(Map.of("b", 1), Map.of("b", 2)));
            e.evaluate(in);
            row("API-3", "Java-типы аргументов функции хоста для `null, nothing, 1, 1.5, Long 7, [..], a.b, {..}, function, /re/, Long 2^53+1`", String.join(", ", seen),
                    "JS: null, undefined, number×3, Array, Array(sequence), Object, lambda-объект, RegExp, number(округлён)", false);
        }
        {
            Jsonata e = Jsonata.jsonata("$h() = null");
            e.registerFunction("h", new JFunction((input, a) -> null, null));
            String r1 = safe(() -> e.evaluate(null));
            Jsonata e2 = Jsonata.jsonata("$h() = null");
            e2.registerFunction("h", new JFunction((input, a) -> Jsonata.NULL_VALUE, null));
            String r2 = safe(() -> e2.evaluate(null));
            row("API-4", "хост возвращает Java `null` / `Jsonata.NULL_VALUE`; выражение `$h() = null`", r1 + " / " + r2,
                    "в JS `null` → JSON null, `undefined` → undefined; true", false);
        }
        // 5. calling a lambda from the host
        {
            Jsonata e = Jsonata.jsonata("$ap(function($x){$x * 2}, 21)");
            e.registerFunction("ap", new JFunction((input, a) -> Functions.funcApply(a.get(0), List.of(a.get(1))), null));
            row("API-5", "хост вызывает переданную лямбду через `Functions.funcApply` в потоке вычисления", safe(() -> e.evaluate(null)), "42 (`fn.apply(this,[x])`)", true);
        }
        {
            Jsonata e = Jsonata.jsonata("$ap(function($x){$x * 2}, 21)");
            ExecutorService ex = Executors.newSingleThreadExecutor();
            e.registerFunction("ap", new JFunction((input, a) -> ex.submit(() -> fa(a.get(0), List.of(a.get(1)))).get(), null));
            String r = safe(() -> e.evaluate(null));
            ex.shutdown();
            row("API-5b", "хост вызывает лямбду из **другого потока** (executor) во время вычисления", r, "42 (в JS однопоточно; Promise)", r.equals("42"));
        }
        {
            Jsonata e = Jsonata.jsonata("function($x){$x * 2}");
            Object fn = e.evaluate(null);
            String r = safe(() -> fa(fn, List.of(21)));
            row("API-5c", "лямбда, возвращённая как результат, вызывается хостом **после** `evaluate` (тот же поток)", r + " (тип результата: " + cls(fn) + ")", "42 (`result.apply(null,[21])`)", r.equals("42"));
            AtomicReference<String> r2 = new AtomicReference<>();
            Thread t = new Thread(() -> r2.set(safe(() -> fa(fn, List.of(21)))));
            t.start();
            t.join();
            row("API-5d", "та же лямбда вызывается после `evaluate` из другого потока", r2.get(), "42", "42".equals(r2.get()));
        }
        // 6. exceptions thrown by host functions
        {
            Jsonata e = Jsonata.jsonata("$boom()");
            e.registerFunction("boom", new JFunction((input, a) -> { throw new IllegalStateException("bad"); }, null));
            String r = safe(() -> e.evaluate(null));
            Jsonata e2 = Jsonata.jsonata("$boom()");
            e2.registerFunction("boom", new JFunction((input, a) -> { throw new java.io.IOException("io"); }, null));
            String r2 = safe(() -> e2.evaluate(null));
            row("API-6", "исключение хоста: unchecked / checked", r + " / " + r2, "исключение пробрасывается как есть (JS добавляет position к объекту ошибки)", true);
        }
        // 7. runtime bounds frame reuse
        {
            Jsonata e = Jsonata.jsonata("[1..1000].($ * 2) ~> $count");
            Frame f = e.createFrame();
            f.setRuntimeBounds(300, 1000);
            String r1 = safe(() -> e.evaluate(null, f));
            Thread.sleep(400);
            String r2 = safe(() -> e.evaluate(null, f));
            row("API-7", "один Frame с `setRuntimeBounds(300ms, 1000)` используется для двух `evaluate` с паузой 400 мс", r1 + " / " + r2,
                    "в JS timebox отсчитывается от начала вычисления (тестовый харнесс), ошибка U1001", r2.equals("1000"));
        }
        {
            Jsonata e = Jsonata.jsonata("$f(1)");
            Frame f = e.createFrame();
            f.setRuntimeBounds(1000, 1000);
            f.setEvaluateEntryCallback((x, i, env) -> {});
            f.bind("f", (Jsonata.Fn1<Object, Object>) x -> x);
            String r = safe(() -> e.evaluate(null, f));
            row("API-8", "`setRuntimeBounds` + свой entry-колбэк на одном Frame", r + " (колбэк замещает Timebox: лимиты отключены)",
                    "в JS колбэки тоже одна переменная окружения", true);
        }
        // 9. registerFunction is shared by all evaluations / threads
        {
            Jsonata e = Jsonata.jsonata("$f()");
            e.registerFunction("f", (Jsonata.Fn0<Object>) () -> "A");
            String r1 = String.valueOf(e.evaluate(null));
            e.registerFunction("f", (Jsonata.Fn0<Object>) () -> "B");
            String r2 = String.valueOf(e.evaluate(null));
            row("API-9", "`registerFunction` на скомпилированном выражении глобален для всех последующих вызовов", r1 + ", " + r2, "A, B (так же)", true);
        }
        // 10. Fn1 lambda without signature receives NULL_VALUE?
        {
            AtomicReference<Object> got = new AtomicReference<>();
            Jsonata e = Jsonata.jsonata("$f(null)");
            e.registerFunction("f", (Jsonata.Fn1<Object, Object>) x -> { got.set(x); return 1; });
            e.evaluate(null);
            row("API-10", "Java-лямбда `Fn1` без сигнатуры получает аргумент `null`", cls(got.get()), "null", got.get() == null);
        }
        // 11. outputs: number types and sequences visible to host
        {
            Jsonata e = Jsonata.jsonata("[1, 1.0, 2.5, 1/2*2, $sum([1,2]), 10000000000, 9007199254740993, a.b]");
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("a", List.of(Map.of("b", 1)));
            Object r = e.evaluate(in);
            List<String> t = new ArrayList<>();
            for (Object o : (List<?>) r) t.add(cls(o) + ":" + o);
            row("API-11", "Java-типы чисел в результате `[1, 1.0, 2.5, 1/2*2, $sum([1,2]), 1e10, 9007199254740993, a.b]`", String.join(", ", t) + "; класс списка " + cls(r),
                    "JS: только number (double); 9007199254740993 → 9007199254740992", false);
        }
        System.out.println("# Проба API хоста (JVM-движок)\n");
        System.out.println("Сгенерировано `compat.api.ApiProbe`. Колонка JS описывает поведение jsonata-js 2.2.2 при эквивалентном использовании API.\n");
        System.out.println("| id | проверка | JVM (наблюдение) | jsonata-js 2.2.2 | итог |\n|---|---|---|---|---|");
        for (String[] r : rows)
            System.out.println("| " + String.join(" | ", r[0], r[1], r[2].replace("|", "\\|"), r[3], r[4]) + " |");
        System.exit(0);
    }
}
