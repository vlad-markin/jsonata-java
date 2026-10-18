package com.dashjoin.jsonata;

import static com.dashjoin.jsonata.Jsonata.jsonata;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

/**
 * Behaviour aligned with jsonata-js 2.2.2. Each test refers to an id of
 * compat-eval/divergences.md.
 */
public class CompatJs222Test {

    /** API-01: evaluations of one compiled expression must not share variables. */
    @Test
    public void api01_topLevelAssignmentIsolated() {
        Jsonata e = jsonata("$y := ($exists($y) ? $y + 1 : 1)");
        assertEquals(1, e.evaluate(null));
        assertEquals(1, e.evaluate(null));
    }

    /** API-01: concurrent evaluations without a bindings frame see their own $$. */
    @Test
    public void api01_concurrentRootReference() throws Exception {
        Jsonata e = jsonata("[1..200].($$.id) ~> $distinct()");
        ExecutorService ex = Executors.newFixedThreadPool(8);
        try {
            List<Future<Object>> fs = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                final int id = i;
                fs.add(ex.submit(() -> {
                    Map<String, Object> in = new LinkedHashMap<>();
                    in.put("id", id);
                    return e.evaluate(in);
                }));
            }
            for (int i = 0; i < fs.size(); i++)
                assertEquals(i, fs.get(i).get());
        } finally {
            ex.shutdown();
        }
    }

    /** NUM-01: deep equality compares numbers by value regardless of the Java type. */
    @Test
    public void num01_deepEqualityIgnoresNumberType() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("a", List.of(1L, 2L));
        in.put("d", List.of(1.0, 2.0));
        in.put("o", Map.of("x", 1L));
        assertEquals(true, jsonata("a = [1, 2]").evaluate(in));
        assertEquals(true, jsonata("d = [1, 2]").evaluate(in));
        assertEquals(false, jsonata("a != [1, 2]").evaluate(in));
        assertEquals(true, jsonata("o = {'x': 1.0}").evaluate(in));
        assertEquals(false, jsonata("o = {'x': 2}").evaluate(in));
        assertEquals(true, jsonata("[1, {'a': [null]}] = [1.0, {'a': [null]}]").evaluate(null));
        assertEquals(List.of(1L, 2L), jsonata("$distinct([a, 1, 2.0, a[0]])").evaluate(in));
    }

    /** NUM-01: "in" uses strict equality like jsonata-js: numbers by value, objects/arrays by identity. */
    @Test
    public void num01_inOperatorStrictEquality() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("n", 3L);
        assertEquals(true, jsonata("n in [1, 2, 3.0]").evaluate(in));
        assertEquals(false, jsonata("[1] in [[1]]").evaluate(null));
        assertEquals(false, jsonata("{'a':1} in [{'a':1}]").evaluate(null));
        assertEquals(true, jsonata("($o := {'a':1}; $o in [$o])").evaluate(null));
    }

    /** SEQ-01: $distinct of an array (not a sequence) keeps the array even with one distinct value. */
    @Test
    public void seq01_distinctKeepsArray() {
        assertEquals(List.of(1), jsonata("$distinct([1, 1])").evaluate(null));
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("a", List.of(Map.of("b", 1), Map.of("b", 1)));
        assertEquals(1, jsonata("$distinct(a.b)").evaluate(in));
    }

    /** SORT-01: default $sort on mixed Integer/Double values, D3070 instead of ClassCastException. */
    @Test
    public void sort01_defaultComparator() {
        assertEquals(List.of(0.5, 2), jsonata("[2, 0.5] ~> $sort").evaluate(null));
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("n", List.of(3L, 1.5, 2));
        assertEquals(List.of(1.5, 2, 3L), jsonata("$sort(n)").evaluate(in));
        JException e = org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$sort([{'a':1},{'a':2}])").evaluate(null));
        assertEquals("D3070", e.getError());
        e = org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> jsonata("$sort([1,'a'])").evaluate(null));
        assertEquals("D3070", e.getError());
    }

    /** SORT-02: same merge sort as jsonata-js, also for non-strict comparators. */
    @Test
    public void sort02_mergeSortLikeJs() {
        assertEquals(List.of(2, 1, 3), jsonata("$sort([3,1,2], function($l,$r){true})").evaluate(null));
        assertEquals(List.of("f", "d", "b", "c", "a", "e"), jsonata(
                "$sort([{'k':2,'n':'a'},{'k':1,'n':'b'},{'k':2,'n':'c'},{'k':1,'n':'d'},{'k':3,'n':'e'},{'k':1,'n':'f'}], function($l,$r){$l.k >= $r.k}).n")
                .evaluate(null));
        // stable for strict comparators
        assertEquals(List.of("b", "d", "a", "c"), jsonata(
                "$sort([{'k':2,'n':'a'},{'k':1,'n':'b'},{'k':2,'n':'c'},{'k':1,'n':'d'}], function($l,$r){$l.k > $r.k}).n")
                .evaluate(null));
    }

    /** SORT-03: order-by raises the error for the same pair of values as jsonata-js. */
    @Test
    public void sort03_orderByErrorCode() {
        JException e = org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("items^(k)").evaluate(Map.of("items", List.of(Map.of("k", 1), Map.of("k", "9"),
                        mapOfNull("k"), Map.of("k", "10"), Map.of("k", 2), Map.of("n", "nok")))));
        assertEquals("T2008", e.getError());
        // equal numbers of different Java types are equal for order-by (stable)
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("items", List.of(Map.of("k", 1L, "n", "a"), Map.of("k", 1.0, "n", "b"), Map.of("k", 1, "n", "c")));
        assertEquals(List.of("a", "b", "c"), jsonata("items^(k).n").evaluate(in));
    }

    static Map<String, Object> mapOfNull(String key) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, null);
        return m;
    }

    /** UND-01: JSON null passed as a function argument is a value, not undefined. */
    @Test
    public void und01_nullArgumentIsValue() {
        assertEquals(1, jsonata("$count(null)").evaluate(null));
        assertEquals(1, jsonata("$count(owner)").evaluate(mapOfNull("owner")));
        assertEquals("null", jsonata("$map(null, function($v){$type($v)})").evaluate(null));
        Jsonata e = jsonata("$sort(null)");
        e.setOutputConvertNulls(false);
        assertEquals(List.of(Jsonata.NULL_VALUE), e.evaluate(null));
        assertEquals("T0412", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$max(null)").evaluate(null)).getError());
    }

    /** UND-02: signature validation rejects null for typed parameters (T0410), also for host functions. */
    @Test
    public void und02_signatureRejectsNull() {
        assertEquals("T0410", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$uppercase(null)").evaluate(null)).getError());
        assertEquals("T0410", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$length(a)").evaluate(mapOfNull("a"))).getError());
        Jsonata e = jsonata("$hu(null)");
        e.registerFunction("hu", new Jsonata.JFunction((input, args) -> "x", "<s:s>"));
        assertEquals("T0410", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> e.evaluate(null)).getError());
        assertEquals("T0410", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$map([null], $uppercase)").evaluate(null)).getError());
    }

    /** NUM-02: numbers are stringified like jsonata-js (ECMAScript Number#toString, 15 significant digits). */
    @Test
    public void num02_numberToString() {
        assertEquals("100000000000000000000", jsonata("$string(1e20)").evaluate(null));
        assertEquals("1e+21", jsonata("$string(1e21)").evaluate(null));
        assertEquals("12345678901234567000", jsonata("12345678901234567890 & ''").evaluate(null));
        assertEquals("18446744073709552000", jsonata("$string($power(2, 64))").evaluate(null));
        assertEquals("0.3", jsonata("$string(0.1 + 0.2)").evaluate(null));
        assertEquals("1e-7", jsonata("$string(1e-7)").evaluate(null));
        assertEquals("0.000001", jsonata("$string(1e-6)").evaluate(null));
        assertEquals("5e-324", jsonata("$string(5e-324)").evaluate(null));
        assertEquals("[1.5,100000000000000000000]", jsonata("$string([1.5, 1e20])").evaluate(null));
    }

    /** STR-04: $string escapes like JSON.stringify and prints empty objects as {} when prettified. */
    @Test
    public void str04_stringEscapingAndPrettyPrint() {
        assertEquals("{\"a\":\"\u20ac \u201cq\u201d \u2013 \u2026 \u0085 \\ud800 \u2028\"}",
                jsonata("$string({'a': '\u20ac \u201cq\u201d \u2013 \u2026 \u0085 \ud800 \u2028'})").evaluate(null));
        assertEquals("{}", jsonata("$string({}, true)").evaluate(null));
        assertEquals("{\n  \"a\": {\n    \"b\": {}\n  }\n}", jsonata("$string({'a':{'b':{}}}, true)").evaluate(null));
        assertEquals("[\n  [],\n  {}\n]", jsonata("$string([[],{}], true)").evaluate(null));
        assertEquals("[\"\ud83d\ude00\"]", jsonata("$string(['\ud83d\ude00'])").evaluate(null));
    }

    /** STR-01: $substring counts code points (surrogate pairs are one character) with slice semantics. */
    @Test
    public void str01_substringCodePoints() {
        assertEquals("\ud83d\ude00b\ud83d\ude00", jsonata("$substring('a\ud83d\ude00b\ud83d\ude00c', 1, 3)").evaluate(null));
        assertEquals("\ud83d\ude00c", jsonata("$substring('a\ud83d\ude00b\ud83d\ude00c', -2)").evaluate(null));
        assertEquals("\ud83d\ude00", jsonata("$substring('\ud83d\ude00', -3)").evaluate(null));
        assertEquals("bc", jsonata("$substring('abc', 1.5, 1.5)").evaluate(null));
        assertEquals("", jsonata("$substring('abc', 1, 0)").evaluate(null));
    }

    /** STR-02: URL functions follow ECMAScript encodeURI(Component)/decodeURI(Component). */
    @Test
    public void str02_urlFunctions() {
        assertEquals("https://x.y/a%20b?q=%C3%A9#f", jsonata("$encodeUrl('https://x.y/a b?q=\u00e9#f')").evaluate(null));
        assertEquals("a%20b%26c%3D%F0%9F%98%80", jsonata("$encodeUrlComponent('a b&c=\ud83d\ude00')").evaluate(null));
        assertEquals("a+b", jsonata("$decodeUrlComponent('a+b')").evaluate(null));
        assertEquals("a%2Fb ", jsonata("$decodeUrl('a%2Fb%20')").evaluate(null));
        assertEquals("D3140", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$decodeUrl('%')").evaluate(null)).getError());
        assertEquals("D3140", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$encodeUrlComponent('\ud800')").evaluate(null)).getError());
    }

    /** PART-01: only '?' placeholders are parameters of a partially applied function. */
    @Test
    public void part01_partialApplicationPlaceholders() {
        assertEquals(List.of("", ""), jsonata("$map(['a','b'], $substring(?, 1))").evaluate(null));
        assertEquals("bc", jsonata("($f := $substring(?, 1); $f('abc', 5))").evaluate(null));
        assertEquals(List.of(List.of(1, 2), List.of(2, 2)),
                jsonata("($p := function($a,$b,$c){[$a,$b,$c]}(?, 2); $map([1,2], $p))").evaluate(null));
        assertEquals("a", jsonata("$substring(?, 0, ?)('abc', 1)").evaluate(null));
    }

    static String err(String expr) {
        return org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> jsonata(expr).evaluate(null)).getError();
    }

    /** RE-01/RE-02: JavaScript regex syntax and semantics. */
    @Test
    public void re01_re02_jsRegexDialect() {
        assertEquals("x", jsonata("$replace('ab', /[^]+/, 'x')").evaluate(null));
        assertEquals(false, jsonata("$contains('A', /\\0/)").evaluate(null));
        assertEquals(true, jsonata("$contains('aZ', /\\Z/)").evaluate(null)); // identity escape = 'Z'
        assertEquals("a_b_c", jsonata("$replace('a\u00a0b\u2028c', /\\s/, '_')").evaluate(null));
        assertEquals(false, jsonata("$contains('abc\n', /c$/)").evaluate(null));
        assertEquals(true, jsonata("$contains('ab\ncd', /b$/m)").evaluate(null));
        assertEquals(true, jsonata("$contains('\u00e9', /\u00c9/i)").evaluate(null));
        assertEquals(true, jsonata("$contains('a{,2}', /a{,2}/)").evaluate(null));
        org.junit.jupiter.api.Assertions.assertThrows(java.util.regex.PatternSyntaxException.class,
                () -> jsonata("$contains('aa', /a++/)"));
    }

    /** RE-03: zero-length matches raise D1004 while iterating, like jsonata-js. */
    @Test
    public void re03_zeroLengthMatch() {
        assertEquals("D1004", err("$match('ab', /x*/)"));
        assertEquals("D1004", err("$replace('abracadabra', /.*?/, '$1')"));
        assertEquals("D1004", err("$split('ab', /x*/)"));
        assertEquals(true, jsonata("$contains('ab', /x*/)").evaluate(null));
        assertEquals(List.of("", ""), jsonata("$split('42', /.*/)").evaluate(null));
    }

    /** RE-04: match object of a regex used as a function; custom matcher functions. */
    @Test
    public void re04_matchObjectAndCustomMatchers() {
        Map<?, ?> m = (Map<?, ?>) jsonata("'abc' ~> /b/").evaluate(null);
        assertEquals(List.of("match", "start", "end", "groups", "next"), new ArrayList<>(m.keySet()));
        assertEquals(List.of(), m.get("groups"));
        assertEquals(1, ((Map<?, ?>) jsonata("$map(['ab'], /b/)").evaluate(null)).get("start"));
        assertEquals(List.of("a", "b"), jsonata(
                "$split('aXb', function($s){ $s ~> /X/ })").evaluate(null));
    }

    /** RE-05: the limit argument of $match. */
    @Test
    public void re05_matchLimit() {
        assertEquals(List.of("1", "2"), jsonata("$match('a1b2c3', /\\d/, 2).match").evaluate(null));
    }

    /** RE-06: $replace replacement strings and errors. */
    @Test
    public void re06_replace() {
        assertEquals("a$c", jsonata("$replace('abc', /b/, '$$')").evaluate(null));
        assertEquals("a[]c", jsonata("$replace('abc', /(b)/, '[$2]')").evaluate(null));
        assertEquals("x.b", jsonata("$replace('a.b', 'a', 'x', 1)").evaluate(null));
        assertEquals("ax", jsonata("$replace('a.', '.', 'x', 1)").evaluate(null));
        assertEquals("D3012", err("$replace('abc', /b/, function($m){42})"));
        assertEquals("D3010", err("$replace('abc', '', 'x')"));
        assertEquals("D3011", err("$replace('abc', 'b', 'x', -1)"));
    }

    /** API-03: partial application of host functions; host functions without signature in HOFs. */
    @Test
    public void api03_hostFunctionsPartialAndHof() {
        Jsonata e = jsonata("$ap($ap(?, 'x'), function($s){$s & $s})");
        e.registerFunction("ap", new Jsonata.JFunction((input, args) -> {
            try {
                return Functions.funcApply(args.get(0), List.of(args.get(1)));
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }, null));
        assertEquals("xx", e.evaluate(null));
        Jsonata e2 = jsonata("$map(['a','b'], $echo)");
        e2.registerFunction("echo", new Jsonata.JFunction((input, args) -> args.get(0), null));
        assertEquals(List.of("a", "b"), e2.evaluate(null));
    }

    /** API-04: guardrail options (jsonata-js 2.2): timeout/stack without sequence, sequence limit in $append and wildcards. */
    @Test
    public void api04_guardrails() {
        assertEquals(List.of(1, 2, 3, 4, 5), jsonata("[1..5]", Jsonata.Options.builder().timeout(1000L).build()).evaluate(null));
        assertEquals(List.of(1, 2, 3, 4, 5), jsonata("[1..5]", Jsonata.Options.builder().stack(100).build()).evaluate(null));
        Jsonata.Options seq = Jsonata.Options.builder().sequence(5).build();
        assertEquals("D2015", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("$append([1,2,3],[4,5,6])", seq).evaluate(null)).getError());
        Jsonata.Options seq3 = Jsonata.Options.builder().sequence(3).build();
        assertEquals("D2015", org.junit.jupiter.api.Assertions.assertThrows(JException.class,
                () -> jsonata("{'a':[1,2],'b':[3,4]}.*", seq3).evaluate(null)).getError());
    }

    /** API-05: setRuntimeBounds raises U1001 and measures the time of each evaluation. */
    @Test
    public void api05_runtimeBounds() throws Exception {
        Jsonata e = jsonata("($f := function($n){$n=0?0:1+$f($n-1)}; $f(50))");
        Jsonata.Frame f = e.createFrame();
        f.setRuntimeBounds(1000, 30);
        assertEquals("U1001", org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> e.evaluate(null, f)).getError());
        // the same frame can be reused after the time limit has passed since its creation
        Jsonata e2 = jsonata("[1..100].($ * 2) ~> $count");
        Jsonata.Frame f2 = e2.createFrame();
        f2.setRuntimeBounds(300, 1000);
        assertEquals(100, e2.evaluate(null, f2));
        Thread.sleep(400);
        assertEquals(100, e2.evaluate(null, f2));
    }

    /** NUM-05: $number accepts the same strings as jsonata-js and raises D3030 otherwise. */
    @Test
    public void num05_number() {
        assertEquals(1000, jsonata("$number('1e3')").evaluate(null));
        assertEquals(31, jsonata("$number('0x1F')").evaluate(null));
        assertEquals(3, jsonata("$number('0b11')").evaluate(null));
        for (String s : new String[] { "'+5'", "'1.'", "'.5'", "'  42 '", "'Hello'", "''", "'1e400'", "'Infinity'" })
            assertEquals("D3030", err("$number(" + s + ")"), s);
    }

    /** NUM-07: $sum / $average add like JavaScript (no compensated summation). */
    @Test
    public void num07_sumAverage() {
        assertEquals(0.6000000000000001, jsonata("$sum([0.1, 0.2, 0.3])").evaluate(null));
        assertEquals(0.20000000000000004, jsonata("$average([0.1, 0.2, 0.3])").evaluate(null));
    }

    static JException ex(String expr, Object input) {
        return org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> jsonata(expr).evaluate(input));
    }

    /** ERR-01: runtime errors carry the position of the operator / constructor like jsonata-js. */
    @Test
    public void err01_positions() {
        Map<String, Object> in = Map.of("items", List.of(Map.of("k", 1, "n", "a")));
        assertEquals(6, ex("items{k: n}", in).getLocation());
        assertEquals(13, ex("($x := 1; $x())", null).getLocation());
        assertEquals(6, ex("null + 1", null).getLocation());
        assertEquals(1, ex("{'x': 1, 'x': 2}", null).getLocation());
        assertEquals(1, ex("-'a'", null).getLocation());
        assertEquals(6, ex("[1.5..2]", null).getLocation());
        assertEquals(6, ex("$sqrt(-1)", null).getLocation());
    }

    /** ERR-04: error messages are formatted like jsonata-js populateMessage. */
    @Test
    public void err04_messages() {
        assertEquals("Argument 2 of function \"min\" does not match function signature", ex("$min(2, 3)", null).getMessage());
        assertEquals("The values 1 and \"a\" either side of operator \"<\" must be of the same data type", ex("1 < 'a'", null).getMessage());
        assertEquals("Argument 1 of function \"average\" must be an array of \"numbers\"", ex("$average(['1','2'])", null).getMessage());
        assertEquals("Cannot negate a non-numeric value: \"a\"", ex("-'a'", null).getMessage());
        assertEquals("The left side of the \"+\" operator must evaluate to a number", ex("'a' + 1", null).getMessage());
        assertEquals("$error() function evaluated", ex("$error()", null).getMessage());
        assertEquals("$assert() statement failed", ex("$assert(false, '')", null).getMessage());
        assertEquals("The radix of the formatBase function must be between 2 and 36.  It was given 1", ex("$formatBase(1, 1)", null).getMessage());
    }

    /** ERR-02: partial application of a function name without '$' raises T1007. */
    @Test
    public void err02_partialWithoutDollar() {
        assertEquals("T1007", err("substring(?, 0, ?)"));
        assertEquals("T1008", err("$nothing(?, 0)"));
    }

    static Object call(Object fn, Object... args) {
        try {
            return Functions.funcApply(fn, new ArrayList<>(List.of(args)));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** API-02: function values can be called by the host from other threads and after evaluate. */
    @Test
    public void api02_callLambdaFromOtherThread() throws Exception {
        Object fn = jsonata("($k := 2; function($x){$x * $k})").evaluate(null);
        assertEquals(42, call(fn, 21));
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            assertEquals(42, ex.submit(() -> call(fn, 21)).get());
            Jsonata e = jsonata("$ap(function($x){$x * 2}, 21)");
            e.registerFunction("ap", new Jsonata.JFunction((input, args) -> ex.submit(() -> call(args.get(0), args.get(1))).get(), null));
            assertEquals(42, e.evaluate(null));
        } finally {
            ex.shutdown();
        }
    }

    /** API-07: $now()/$millis() of a cached expression return the time of its own evaluation. */
    @Test
    public void api07_evaluationTimePerExpression() throws Exception {
        Jsonata cached = jsonata("$millis()");
        Jsonata other = jsonata("1");
        other.evaluate(null);
        Thread.sleep(300);
        long t = ((Number) cached.evaluate(null)).longValue();
        org.junit.jupiter.api.Assertions.assertTrue(System.currentTimeMillis() - t < 200, "stale $millis()");
        // a nested evaluation of another expression (from a host function) restores the context
        Jsonata outer = jsonata("[$millis(), $inner(), $millis()]");
        Jsonata inner = jsonata("$millis()");
        outer.registerFunction("inner", (Jsonata.Fn0<Object>) () -> inner.evaluate(null));
        List<?> r = (List<?>) outer.evaluate(null);
        assertEquals(r.get(0), r.get(2));
    }

    /** NUM-03: $formatNumber is a port of the jsonata-js implementation (picture analysis, rounding, errors). */
    @Test
    public void num03_formatNumber() {
        assertEquals(".1", jsonata("$formatNumber(0.125, '#.#')").evaluate(null));
        assertEquals("0.00", jsonata("$formatNumber(0.005, '0.00')").evaluate(null));
        assertEquals("1.00", jsonata("$formatNumber(1.005, '0.00')").evaluate(null));
        assertEquals("2", jsonata("$formatNumber(2.5, '0')").evaluate(null));
        assertEquals("0.23e0", jsonata("$formatNumber(0.234, '#.00e0')").evaluate(null));
        assertEquals("12.3e-05", jsonata("$formatNumber(0.000123, '00.0e00')").evaluate(null));
        assertEquals("87,504.4812", jsonata("$formatNumber(2392.14*(-36.58), '000,000.000###;###,###.000###')").evaluate(null));
        assertEquals("12,34,568", jsonata("$formatNumber(1234567.891, '##,##,##0')").evaluate(null));
        assertEquals(",5,", jsonata("$formatNumber(5, '##,##,##0')").evaluate(null));
        assertEquals("1,234.5,0", jsonata("$formatNumber(1234.5, '0,000.0,0')").evaluate(null));
        assertEquals("1e,+22.00", jsonata("$formatNumber(1e22, '#,##0.00')").evaluate(null));
        assertEquals("-1%", jsonata("$formatNumber(-1, '0%', {'percent': 'pc'})").evaluate(null));
        assertEquals("-1.234,57", jsonata("$formatNumber(-1234.567, '#.##0,00', {'decimal-separator': ',', 'grouping-separator': '.'})").evaluate(null));
        assertEquals("\uff11,\uff12\uff13\uff14.\uff15\uff17",
                jsonata("$formatNumber(1234.567, '#,##\uff10.\uff10\uff10', {'zero-digit': '\uff10'})").evaluate(null));
        assertEquals("~5.0e~1", jsonata("$formatNumber(-0.5, '0.0e0', {'minus-sign': '~'})").evaluate(null));
        assertEquals("D3080", err("$formatNumber(1, '0;0;0')"));
        assertEquals("D3081", err("$formatNumber(1, '0..0')"));
        assertEquals("D3082", err("$formatNumber(20, '#0%%')"));
        assertEquals("D3084", err("$formatNumber(1, '0%\u2030')"));
        assertEquals("D3085", err("$formatNumber(1, '.')"));
        assertEquals("D3086", err("$formatNumber(42, '---')"));
        assertEquals("D3087", err("$formatNumber(1, '0.,0')"));
        assertEquals("D3088", err("$formatNumber(1, '0,')"));
        assertEquals("D3089", err("$formatNumber(1, '0,,0')"));
        assertEquals("D3090", err("$formatNumber(1, '0#')"));
        assertEquals("D3091", err("$formatNumber(1, '0.#0')"));
        assertEquals("D3092", err("$formatNumber(1, '0e0%')"));
        assertEquals("D3093", err("$formatNumber(1, '0e#')"));
    }

    /** NUM-04: $formatInteger / $parseInteger are ports of jsonata-js and compute with doubles. */
    @Test
    public void num04_formatParseInteger() {
        assertEquals("ten billion trillion", jsonata("$formatInteger(1e22, 'w')").evaluate(null));
        assertEquals("one thousand trillion trillion", jsonata("$formatInteger(1e27, 'w')").evaluate(null));
        assertEquals("1e+21", jsonata("$formatInteger(1e21, '0')").evaluate(null));
        assertEquals("1,234,567th", jsonata("$formatInteger(1234567, '#,##0;o')").evaluate(null));
        assertEquals("12,34,567", jsonata("$formatInteger(1234567, '0,00,000')").evaluate(null));
        assertEquals("\u0661\u0662\u0663", jsonata("$formatInteger(123, '\u0660')").evaluate(null));
        assertEquals("-1", jsonata("$formatInteger(-0.5, '0')").evaluate(null));
        assertEquals("MMXXVI", jsonata("$formatInteger(2026, 'I')").evaluate(null));
        assertEquals("ab", jsonata("$formatInteger(28, 'a')").evaluate(null));
        assertEquals("D3130", err("$formatInteger(22, '#;o')"));
        assertEquals("D3131", err("$formatInteger(12340, '##0\uff10')"));
        assertEquals(1e46, jsonata("$parseInteger('ten billion trillion trillion trillion', 'w')").evaluate(null));
        assertEquals(12340, jsonata("$parseInteger('\u0661\u0662\u0663\u0664\u0660', '###\u0661')").evaluate(null));
        assertEquals(-5, jsonata("$parseInteger('-5', '1')").evaluate(null));
        assertEquals(12, jsonata("$parseInteger('12', '1')").evaluate(null));
        assertEquals(31, jsonata("$parseInteger('0x1F', '0')").evaluate(null));
        assertEquals(1234, jsonata("$parseInteger('1,234', '#,##0')").evaluate(null));
        assertEquals(Double.NaN, jsonata("$parseInteger('ab', 'i')").evaluate(null));
        assertEquals(Double.NaN, jsonata("$parseInteger('12', '0;o')").evaluate(null));
        assertEquals("D3130", err("$parseInteger('50', '#')"));
        // NaN behaves like in jsonata-js
        assertEquals("object", jsonata("$type($parseInteger('ab', 'i'))").evaluate(null));
        assertEquals(false, jsonata("$boolean($parseInteger('ab', 'i'))").evaluate(null));
        assertEquals(false, jsonata("$parseInteger('ab', 'i') > 1").evaluate(null));
        assertEquals("D3001", err("$string($parseInteger('ab', 'i'))"));
        assertEquals("[null]", jsonata("$string([$parseInteger('ab', 'i')])").evaluate(null));
    }

    /** NUM-06: $formatBase and the range operator compute with doubles like jsonata-js. */
    @Test
    public void num06_formatBaseAndRangeBeyondLong() {
        assertEquals("1e+22", jsonata("$formatBase(1e22)").evaluate(null));
        assertEquals("21e19e0c9bab2400000", jsonata("$formatBase(1e22, 16)").evaluate(null));
        assertEquals("20000000000000", jsonata("$formatBase(9007199254740993, 16)").evaluate(null));
        assertEquals("2", jsonata("$formatBase(2.5)").evaluate(null));
        assertEquals("4", jsonata("$formatBase(3.5)").evaluate(null));
        assertEquals("2s", jsonata("$formatBase(100, 36.5)").evaluate(null));
        assertEquals("1100100", jsonata("$formatBase(100, 1.5)").evaluate(null));
        assertEquals("D3100", err("$formatBase(100, 1.4)"));
        assertEquals("D3100", err("$formatBase(100, 37)"));
        assertEquals(0, jsonata("[1e20..5] ~> $count").evaluate(null));
        assertEquals("T2004", err("[1e22..2.5]"));
        assertEquals("T2003", err("[2.5..1e22]"));
        assertEquals("D2014", err("[1..1e22]"));
        assertEquals("D2014", err("[-1e22..1]"));
        JException e = org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> jsonata("[1..2.5]").evaluate(null));
        assertEquals(4, e.getLocation());
    }

    /** DT-01: $toMillis without picture accepts exactly the jsonata-js ISO 8601 subset (Date.parse semantics). */
    @Test
    public void dt01_toMillisIso8601() {
        assertEquals(1514764800000L, jsonata("$toMillis('2018')").evaluate(null));
        assertEquals(1517443200000L, jsonata("$toMillis('2018-02')").evaluate(null));
        // day overflow rolls over like Date.parse
        assertEquals(1519948800000L, jsonata("$toMillis('2018-02-30')").evaluate(null));
        assertEquals(1517529600000L, jsonata("$toMillis('2018-02-01T24:00:00Z')").evaluate(null));
        assertEquals(1517479200123L, jsonata("$toMillis('2018-02-01T10:00:00.12345678Z')").evaluate(null));
        assertEquals(1517475600000L, jsonata("$toMillis('2018-02-01T10:00:00+0100')").evaluate(null));
        assertEquals(1517392860000L, jsonata("$toMillis('2018-02-01T10:00:00+23:59')").evaluate(null));
        // matches the regex, but Date.parse yields NaN
        Object nan = jsonata("$toMillis('2018-13-01')").evaluate(null);
        assertEquals(Double.NaN, nan);
        assertEquals("D3001", err("$string($toMillis('2018-13-01'))"));
        assertEquals("[null]", jsonata("$string([$toMillis('2018-13-01')])").evaluate(null));
        assertEquals("D3110", err("$toMillis('20180201')"));
        assertEquals("D3110", err("$toMillis('foo')"));
        assertEquals("D3110", err("$toMillis('2018-02-01T09:42')"));
        assertEquals("D3110", err("$toMillis('+002018-02-01')"));
        assertEquals("D3110", err("$toMillis('2018-02-01 09:42:13')"));
    }

    /** DT-02: $toMillis with picture is a port of jsonata-js parseDateTime. */
    @Test
    public void dt02_toMillisPicture() {
        // fixed parse width for adjacent integer markers
        assertEquals(1706745600000L, jsonata("$toMillis('1022024', '[D1][M01][Y0001]')").evaluate(null));
        // fractional seconds with fewer digits than the picture
        assertEquals(1775675104500L, jsonata("$toMillis('2026-04-08T19:05:04.5', '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f1]')").evaluate(null));
        assertEquals(1526947200000L, jsonata("$toMillis('twenty-second of May, 2018', '[Dwo] of [MNn], [Y]')").evaluate(null));
        assertEquals(1526947200000L, jsonata("$toMillis('MMXVIII-v-xxii', '[YI]-[Mi]-[Di]')").evaluate(null));
        assertEquals(1514937600000L, jsonata("$toMillis('2018-1st-3', '[Y]-[M1o]-[D]')").evaluate(null));
        // two digit years are years of the 20th century (Date.UTC)
        assertEquals(-1628812800000L, jsonata("$toMillis('18-05-22', '[Y01]-[M01]-[D01]')").evaluate(null));
        assertEquals(1526947440000L, jsonata("$toMillis('2018-05-22 12:04 am', '[Y]-[M]-[D] [h]:[m] [P]')").evaluate(null));
        assertEquals(1527001440000L, jsonata("$toMillis('2018-05-22 03:04 pm', '[Y]-[M]-[D] [h]:[m] [P]')").evaluate(null));
        assertEquals(1526428800000L, jsonata("$toMillis('2018-136', '[Y]-[d]')").evaluate(null));
        assertEquals(1526963400000L, jsonata("$toMillis('2018-05-22T10:00:00+05:30', '[Y]-[M]-[D]T[H]:[m]:[s][Z]')").evaluate(null));
        // like jsonata-js 2.2.2 (DT-06): '-0130' is read as -00:30 (the offset comes from data, kept equivalent)
        assertEquals(1526985000000L, jsonata("$toMillis('2018-05-22T10:00:00-0130', '[Y]-[M]-[D]T[H]:[m]:[s][Z0101]')").evaluate(null));
        // out of range components roll over like Date.UTC
        assertEquals(1548892800000L, jsonata("$toMillis('2018-13-31', '[Y]-[M]-[D]')").evaluate(null));
        assertEquals(1514764800000L, jsonata("$toMillis('2018', '[Y,2-]')").evaluate(null));
        assertEquals(null, jsonata("$toMillis('x', '[Y]')").evaluate(null));
        assertEquals(null, jsonata("$toMillis('2018-05-22', '[Y]-[M]-[D]#')").evaluate(null));
        assertEquals(null, jsonata("$toMillis('\u0661\u0662', '[Y\u0661]')").evaluate(null));
        assertEquals("D3132", err("$toMillis('2018-05-22', '[Y]-[M]-[q]')"));
        assertEquals("D3133", err("$toMillis('2018-05-22', '[YN]-[M]-[D]')"));
        assertEquals("D3133", err("$toMillis('Thursday, 1 February 2018', '[E]')"));
        assertEquals("D3135", err("$toMillis('2018-05-22', '[Y]-[M')"));
        assertEquals("D3136", err("$toMillis('2018-22', '[Y]-[D]')"));
        // names are looked up case sensitively, the month is then missing
        assertEquals("D3136", err("$toMillis('2018 february 3', '[Y] [MNn] [D]')"));
        assertEquals("D3136", err("$toMillis('2018-3-2-5', '[X]-[x]-[w]-[F1]')"));
        assertEquals("D3136", err("$toMillis('2018-32-5', '[X]-[W]-[F1]')"));
        assertEquals("D3130", err("$toMillis('x', '[Y#]')"));
        // unspecified most significant components default to the evaluation timestamp
        assertEquals(true, jsonata("$toMillis('18', '[F1]') = $millis()").evaluate(null));
    }

    /** DT-03: $fromMillis ISO week-numbering year [X], week of month [w] and its month [x] like jsonata-js. */
    @Test
    public void dt03_fromMillisWeekComponents() {
        assertEquals("2004-W53-6", jsonata("$fromMillis($toMillis('2005-01-01'), '[X0001]-W[W01]-[F1]')").evaluate(null));
        assertEquals("2009-W01-1", jsonata("$fromMillis($toMillis('2008-12-29'), '[X0001]-W[W01]-[F1]')").evaluate(null));
        assertEquals("Week: 5 of January", jsonata("$fromMillis(1359720000000, 'Week: [w] of [xNn]')").evaluate(null));
        assertEquals("Week: 1 of August", jsonata("$fromMillis(1533038400000, 'Week: [w] of [xNn]')").evaluate(null));
        assertEquals("Week: 1 of January", jsonata("$fromMillis(1419940800000, 'Week: [w] of [xNn]')").evaluate(null));
        // Date.UTC maps the years 0..99 to 19xx
        assertEquals("0-1-1", jsonata("$fromMillis(-62135596800000, '[X]-[W]-[d]')").evaluate(null));
        assertEquals("-1-1--2", jsonata("$fromMillis(-62198755200000, '[Y]-[d]-[X]')").evaluate(null));
        // fractional millis are truncated (TimeClip)
        assertEquals("001", jsonata("$fromMillis(1.5, '[f001]')").evaluate(null));
        assertEquals("1969-12-31T23:59:59.999", jsonata("$fromMillis(-1.5, '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001]')").evaluate(null));
    }

    /** DT-04: $fromMillis/$now picture errors are JExceptions, the timezone is read with JavaScript parseInt. */
    @Test
    public void dt04_fromMillisErrorsAndTimezone() {
        assertEquals("D3133", err("$fromMillis(0, '[YN]-[M]-[D]')"));
        assertEquals("D3134", err("$now('[ZN]')"));
        assertEquals("D3134", err("$fromMillis(0, '[Z010101t]')"));
        assertEquals("D3135", err("$fromMillis(0, '[Y]-[M')"));
        assertEquals("D3135", err("$fromMillis(0, '[Y]-[')"));
        assertEquals("D3132", err("$fromMillis(0, '[]')"));
        assertEquals("D3131", err("$fromMillis(0, '[Y0\u0661]')"));
        assertEquals("D3130", err("$fromMillis(0, '[Y#]')"));
        // timezones that are not +hhmm: NaN (invalid date) or the leading digits
        assertEquals("NaN", jsonata("$fromMillis(0, '[Y]', 'Z')").evaluate(null));
        assertEquals("NaN", jsonata("$now('[Y]', 'Z')").evaluate(null));
        assertEquals("0NaN-NaN-NaN NaN:NaN am 0N:aN",
            jsonata("$fromMillis(0, '[Y0001]-[M01]-[D01] [h]:[m01] [P] [Z]', 'EST')").evaluate(null));
        assertEquals("undefined undefined", jsonata("$fromMillis(0, '[FNn] [MNn]', 'Z')").evaluate(null));
        assertEquals("0", jsonata("$fromMillis(0, '[H]', '+01:00')").evaluate(null));
        assertEquals("1:30 +01:30", jsonata("$fromMillis(0, '[H]:[m] [Z]', ' +0130')").evaluate(null));
        assertEquals("0:16 +00:16", jsonata("$fromMillis(0, '[H]:[m] [Z]', '0x10')").evaluate(null));
        assertEquals("19:00 -05", jsonata("$fromMillis(0, '[H01]:[m01] [Z01]', '-0500')").evaluate(null));
        assertEquals("00:00 +0", jsonata("$fromMillis(0, '[H01]:[m01] [Z0]', '-0000')").evaluate(null));
        assertEquals("NaN", jsonata("$fromMillis(1e17, '[Y]')").evaluate(null));
        assertEquals("+1:e+:23", jsonata("$fromMillis(0, '[Z]', '99999999999999999999999')").evaluate(null));
        // unknown component with a presentation modifier, width modifiers
        assertEquals("undefined", jsonata("$fromMillis(0, '[q1]')").evaluate(null));
        assertEquals("January", jsonata("$fromMillis(0, '[MNn,3]')").evaluate(null));
        assertEquals("Ja|70|70|001", jsonata("$fromMillis(0, '[MNn,2-2]|[Y,2]|[Y,*-2]|[D,3-]')").evaluate(null));
        // agreed exception (DT-05): negative offsets with minutes are correct (jsonata-js: "17:30 -06:30", "-01:-30")
        assertEquals("18:30 -05:30", jsonata("$fromMillis(0, '[H01]:[m01] [Z]', '-0530')").evaluate(null));
        assertEquals("23:30 -00:30", jsonata("$fromMillis(0, '[H01]:[m01] [Z01]', '-0030')").evaluate(null));
    }

    /** SUITE-01: unterminated comment and invalid signatures raise S0106/S0401/S0402 like jsonata-js. */
    @Test
    public void suite01_parserErrors() {
        JException e = org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> jsonata("1 /* no end"));
        assertEquals("S0106", e.getError());
        e = org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> jsonata("\u03bb($arg)<n<n>>{$arg}(5)"));
        assertEquals("S0401", e.getError());
        assertEquals(10, e.getLocation());
        e = org.junit.jupiter.api.Assertions.assertThrows(JException.class, () -> jsonata("\u03bb($arr)<(sa<n>)>>{$arr}([[1]])"));
        assertEquals("S0402", e.getError());
        assertEquals(9, e.getLocation());
    }

    /** NUM-10: Infinity/NaN results like jsonata-js (D1001 only when used as operand, D3001 in $string). */
    @Test
    public void num10_nonFiniteResults() {
        assertEquals(Double.POSITIVE_INFINITY, jsonata("1/0").evaluate(null));
        assertEquals(Double.POSITIVE_INFINITY, jsonata("$eval('1/0')").evaluate(null));
        assertEquals(Double.POSITIVE_INFINITY, jsonata("$sum([1/0])").evaluate(null));
        assertEquals(Double.NaN, jsonata("5 % 0").evaluate(null));
        assertEquals("D3001", err("$string(1/0)"));
        assertEquals("D1001", err("$string({'a': 1/0})"));
        assertEquals("D1001", err("1/0 + 1"));
    }

    /** UND-01: $each passes JSON null values of a host map (Java null) to the function. */
    @Test
    public void und01_eachNullValue() {
        Jsonata e = jsonata("$each(obj, function($v){$v})");
        e.setOutputConvertNulls(false);
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("a", null);
        obj.put("b", 1);
        assertEquals(List.of(Jsonata.NULL_VALUE, 1), e.evaluate(Map.of("obj", obj)));
    }

    /** NUM-02: integers beyond 2^53 are stringified from their double value like jsonata-js. */
    @Test
    public void num02_largeIntegersToString() {
        assertEquals("31525197391593470", jsonata("$string(3.5 * 9007199254740993)").evaluate(null));
        assertEquals("9007199254740992", jsonata("$string(n)").evaluate(Map.of("n", 9007199254740993L)));
        assertEquals("[12345678901234567000]", jsonata("$string([n])").evaluate(Map.of("n", 12345678901234567890d)));
    }
}
