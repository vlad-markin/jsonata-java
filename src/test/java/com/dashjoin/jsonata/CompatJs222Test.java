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
}
