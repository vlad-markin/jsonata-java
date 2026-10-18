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
}
