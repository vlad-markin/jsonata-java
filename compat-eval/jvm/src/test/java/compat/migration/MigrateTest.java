package compat.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import compat.diff.Json;

/** Samples with ASTs produced by jsonata-js 2.2.2 (migration/ast.js). */
public class MigrateTest {

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> samples() throws Exception {
        Map<String, Map<String, Object>> m = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                MigrateTest.class.getResourceAsStream("/migration-samples.jsonl"), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null)
                if (!l.isBlank()) {
                    Map<String, Object> c = (Map<String, Object>) Json.parse(l);
                    m.put((String) c.get("id"), c);
                }
        }
        return m;
    }

    static List<Finding> analyze(Map<String, Object> c, boolean all) {
        return Migrate.analyze(c.get("ast"), (String) c.get("expr"), Rules.all(), all);
    }

    static List<String> rules(List<Finding> fs) {
        return fs.stream().map(f -> f.rule).collect(Collectors.toList());
    }

    @Test
    public void negativeTimezoneIsRewritten() throws Exception {
        Map<String, Object> c = samples().get("m1");
        List<Finding> fs = analyze(c, false);
        assertEquals(List.of("DT-05"), rules(fs));
        assertEquals("$fromMillis(ts, '[Y]-[M01]-[D01] [H01]:[m01]', '-0630')", Migrate.applyFixes((String) c.get("expr"), fs));
        assertEquals("$now('[H01]:[m01]', \"-0430\")", Migrate.applyFixes((String) samples().get("m2").get("expr"), analyze(samples().get("m2"), false)));
        // positive offsets and whole hours are not affected
        assertTrue(analyze(samples().get("m14"), false).isEmpty());
    }

    @Test
    public void messagesOnlyWhereNoSafeRewrite() throws Exception {
        Map<String, Map<String, Object>> s = samples();
        assertEquals(List.of("ORD-01"), rules(analyze(s.get("m3"), false)));
        assertEquals(null, Migrate.applyFixes((String) s.get("m3").get("expr"), analyze(s.get("m3"), false)));
        assertEquals(List.of("ORD-01"), rules(analyze(s.get("m4"), false)));
        assertEquals(List.of("STR-03"), rules(analyze(s.get("m5"), false)));
        assertEquals(List.of("NUM-10", "NUM-11"), rules(analyze(s.get("m6"), false)));
        assertEquals(List.of("RE-07"), rules(analyze(s.get("m7"), false)));
        assertEquals(List.of("UND-03"), rules(analyze(s.get("m8"), false)));
    }

    @Test
    public void fixedInForkRulesOnlyWithAll() throws Exception {
        Map<String, Map<String, Object>> s = samples();
        assertTrue(analyze(s.get("m9"), false).isEmpty());
        assertEquals(List.of("API-01"), rules(analyze(s.get("m9"), true)));
        assertEquals(List.of("SORT-02"), rules(analyze(s.get("m10"), true)));
        assertEquals(List.of("NUM-01", "UND-01"), rules(analyze(s.get("m11"), true)));
        assertEquals(List.of("DT-03"), rules(analyze(s.get("m12"), true)));
        assertEquals(List.of("RE-01"), rules(analyze(s.get("m13"), true)));
    }

    @Test
    public void tokenSpans() {
        assertEquals(5, Source.stringStart("$f(x,'abc')", "abc", 10));
        assertEquals(-1, Source.stringStart("$f(x,'a\\'c')", "a'c", 11));
        assertEquals(5, Source.regexStart("$m(s,/a\\/b/i)", 12));
        assertTrue(Source.isArrayIndexKey("10") && !Source.isArrayIndexKey("01") && !Source.isArrayIndexKey("-1")
                && !Source.isArrayIndexKey("4294967295"));
    }
}
