package compat.bench;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;

/**
 * Overhead of evaluation-control features, measured INTERLEAVED: in each round every mode runs
 * once (rotating order), so background load on a shared host hits all modes alike. Reports the
 * median time per mode and the median of per-round ratios mode/plain.
 *
 * java -cp ... compat.bench.ModesBench --queries q.json --data model.json --only a,b --rounds 15 --out f.jsonl
 */
public class ModesBench {
    static final String[] MODES = { "plain", "frame", "novalidate", "callbacks", "bounds", "guardrails" };

    @SuppressWarnings("unchecked")
    public static void main(String[] argv) throws Exception {
        Map<String, String> a = Bench.args(argv);
        List<Object> queries = (List<Object>) compat.diff.Json.parse(Files.readString(Path.of(a.get("queries"))));
        List<String> only = Arrays.asList(a.get("only").split(","));
        int rounds = Integer.parseInt(a.getOrDefault("rounds", "15"));
        int warm = Integer.parseInt(a.getOrDefault("warmup", "5"));
        Object data;
        try (Reader r = new FileReader(a.get("data"))) { data = com.dashjoin.jsonata.json.Json.parseJson(r); }
        Jsonata.Options opts = Jsonata.Options.builder().timeout(600000L).stack(100000).sequence(100_000_000).build();
        for (Object qo : queries) {
            Map<String, Object> q = (Map<String, Object>) qo;
            String name = (String) q.get("name"), ex = (String) q.get("expr");
            if (!only.contains(name)) continue;
            Jsonata[] e = new Jsonata[MODES.length];
            for (int m = 0; m < MODES.length; m++) {
                e[m] = MODES[m].equals("guardrails") ? Jsonata.jsonata(ex, opts) : Jsonata.jsonata(ex);
                if (MODES[m].equals("novalidate")) e[m].setValidateInput(false);
            }
            double[][] t = new double[MODES.length][rounds];
            for (int r = -warm; r < rounds; r++) {
                for (int k = 0; k < MODES.length; k++) {
                    int m = (k + Math.max(r, 0)) % MODES.length;
                    Frame f = null;
                    switch (MODES[m]) {
                        case "frame": f = e[m].createFrame(); break;
                        case "callbacks":
                            f = e[m].createFrame();
                            f.setEvaluateEntryCallback((x, in, env) -> { });
                            f.setEvaluateExitCallback((x, in, env, res) -> { });
                            break;
                        case "bounds": f = e[m].createFrame(); f.setRuntimeBounds(600000, 100000); break;
                        default: break;
                    }
                    System.gc();
                    long s = System.nanoTime();
                    Object res = f == null ? e[m].evaluate(data) : e[m].evaluate(data, f);
                    long d = System.nanoTime() - s;
                    if (res == Bench.class) System.out.print(""); // keep result alive
                    if (r >= 0) t[m][r] = d / 1e6;
                }
            }
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("query", name);
            rec.put("rounds", rounds);
            StringBuilder line = new StringBuilder(String.format("[modes] %-18s", name));
            for (int m = 0; m < MODES.length; m++) {
                double[] s = t[m].clone();
                Arrays.sort(s);
                double[] ratio = new double[rounds];
                for (int r = 0; r < rounds; r++) ratio[r] = t[m][r] / t[0][r];
                Arrays.sort(ratio);
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("median_ms", Bench.median(s));
                mm.put("min_ms", s[0]);
                mm.put("ratio_vs_plain_median", Bench.median(ratio));
                List<Object> raw = new ArrayList<>();
                for (double d : t[m]) raw.add(d);
                mm.put("times_ms", raw);
                rec.put(MODES[m], mm);
                line.append(String.format(" %s=%.1fms(x%.2f)", MODES[m], Bench.median(s), Bench.median(ratio)));
            }
            System.err.println(line);
            Files.writeString(Path.of(a.get("out")), compat.diff.Json.write(rec) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
