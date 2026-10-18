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
}
