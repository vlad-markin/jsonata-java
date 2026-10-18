package compat.migration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import compat.diff.Json;

/**
 * Migration detector and rewriter.
 *
 * <pre>
 * java -cp compat-eval/jvm/target/classes compat.migration.Migrate [--all] [--rewrite] [--warnings] in.jsonl|- out.jsonl|-
 * </pre>
 * Input lines: {"id": ..., "expr": "&lt;expression text&gt;", "ast": &lt;jsonata-js ast() JSON&gt;}.
 * The AST can be produced by any parser compatible with jsonata-js ast() (migration/ast.js uses
 * jsonata-js 2.2.2). The expression text is needed to locate tokens and to rewrite.
 * Output: one line per finding {"id","rule","severity","position","message","recommendation",
 * "fixedInFork","fix":{"start","end","text"}?} and, with --rewrite, one line per rewritten expression
 * {"id","type":"rewrite","expr","rewritten","rules":[...]}.
 */
public class Migrate {

    public static void main(String[] args) throws Exception {
        boolean all = false, rewrite = false, warningsOnly = false;
        List<String> files = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--all")) all = true;
            else if (a.equals("--warnings")) warningsOnly = true;
            else if (a.equals("--rewrite")) rewrite = true;
            else files.add(a);
        }
        BufferedReader in = files.size() > 0 && !files.get(0).equals("-")
                ? Files.newBufferedReader(Path.of(files.get(0)), StandardCharsets.UTF_8)
                : new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = files.size() > 1 && !files.get(1).equals("-")
                ? new PrintStream(Files.newOutputStream(Path.of(files.get(1))), false, StandardCharsets.UTF_8)
                : new PrintStream(System.out, false, StandardCharsets.UTF_8);
        List<Rules.Rule> rules = Rules.all();
        String line;
        int n = 0, withFindings = 0, findings = 0, rewritten = 0;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            n++;
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) Json.parse(line);
            String id = String.valueOf(c.get("id"));
            String expr = (String) c.get("expr");
            List<Finding> fs = analyze(c.get("ast"), expr, rules, all);
            if (warningsOnly)
                fs.removeIf(f -> "info".equals(f.severity));
            if (!fs.isEmpty()) withFindings++;
            findings += fs.size();
            for (Finding f : fs) out.println(Json.write(toMap(id, f)));
            if (rewrite) {
                String r = applyFixes(expr, fs);
                if (r != null && !r.equals(expr)) {
                    rewritten++;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", id);
                    m.put("type", "rewrite");
                    m.put("expr", expr);
                    m.put("rewritten", r);
                    List<Object> ids = new ArrayList<>();
                    for (Finding f : fs) if (f.fixText != null && !ids.contains(f.rule)) ids.add(f.rule);
                    m.put("rules", ids);
                    out.println(Json.write(m));
                }
            }
        }
        out.flush();
        System.err.println("expressions=" + n + " withFindings=" + withFindings + " findings=" + findings + " rewritten=" + rewritten);
    }

    @SuppressWarnings("unchecked")
    public static List<Finding> analyze(Object ast, String expr, List<Rules.Rule> rules, boolean all) {
        List<Finding> fs = new ArrayList<>();
        if (!(ast instanceof Map)) return fs;
        Node root = new Node((Map<String, Object>) ast, null, null);
        for (Rules.Rule r : rules) {
            if (r.fixedInFork() && !all) continue;
            r.check(root, expr, fs);
        }
        return fs;
    }

    /** applies non-overlapping fixes from right to left; null if none */
    public static String applyFixes(String expr, List<Finding> fs) {
        List<Finding> fixes = new ArrayList<>();
        for (Finding f : fs) if (f.fixText != null) fixes.add(f);
        if (fixes.isEmpty()) return null;
        fixes.sort(Comparator.comparingInt((Finding f) -> f.fixStart).reversed());
        StringBuilder b = new StringBuilder(expr);
        int limit = Integer.MAX_VALUE;
        for (Finding f : fixes) {
            if (f.fixEnd > limit) continue; // overlapping
            b.replace(f.fixStart, f.fixEnd, f.fixText);
            limit = f.fixStart;
        }
        return b.toString();
    }

    static Map<String, Object> toMap(String id, Finding f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("rule", f.rule);
        m.put("severity", f.severity);
        m.put("position", f.position);
        m.put("message", f.message);
        m.put("recommendation", f.recommendation);
        m.put("fixedInFork", f.fixedInFork);
        if (f.fixText != null) {
            Map<String, Object> fx = new LinkedHashMap<>();
            fx.put("start", (long) f.fixStart);
            fx.put("end", (long) f.fixEnd);
            fx.put("text", f.fixText);
            m.put("fix", fx);
        }
        return m;
    }
}
