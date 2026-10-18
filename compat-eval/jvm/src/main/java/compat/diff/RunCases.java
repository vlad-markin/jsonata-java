package compat.diff;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import compat.engine.DashjoinEngine;
import compat.engine.Engine;
import compat.engine.EngineError;
import compat.engine.HostFunction;
import compat.engine.Values;

/**
 * JVM side of the diff-runner. Reads cases (JSONL) and writes one result line per case (JSONL).
 *
 * <pre>
 * java -cp target/classes [-Dengine=fully.qualified.EngineClass] compat.diff.RunCases [cases.jsonl|-] [out.jsonl|-]
 * </pre>
 * The supervisor (diff-runner/js/supervise.js) restarts the process on hangs/crashes.
 */
public class RunCases {

    public static Engine loadEngine() throws Exception {
        String cls = System.getProperty("engine");
        if (cls != null && !cls.isEmpty())
            return (Engine) Class.forName(cls).getDeclaredConstructor().newInstance();
        for (Engine e : ServiceLoader.load(Engine.class))
            return e;
        return new DashjoinEngine();
    }

    public static void main(String[] args) throws Exception {
        Engine engine = loadEngine();
        BufferedReader in = args.length > 0 && !args[0].equals("-")
                ? Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)
                : new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = args.length > 1 && !args[1].equals("-")
                ? new PrintStream(Files.newOutputStream(Path.of(args[1])), false, StandardCharsets.UTF_8)
                : new PrintStream(System.out, false, StandardCharsets.UTF_8);
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//"))
                continue;
            out.println(runLine(engine, line));
            out.flush();
        }
        out.close();
    }

    @SuppressWarnings("unchecked")
    public static String runLine(Engine engine, String line) {
        Map<String, Object> c;
        String id = null;
        StringBuilder b = new StringBuilder();
        try {
            c = (Map<String, Object>) Json.parse(line);
            id = String.valueOf(c.get("id"));
        } catch (RuntimeException e) {
            b.append("{\"id\":null,\"engine\":");
            Json.quote(engine.name(), b);
            b.append(",\"status\":\"bad-case\",\"error\":{\"message\":");
            Json.quote(String.valueOf(e), b);
            b.append("}}");
            return b.toString();
        }
        long t0 = System.nanoTime();
        String phase = "setup";
        Object result;
        EngineError error = null;
        result = null;
        try {
            Map<String, Object> bindings = new LinkedHashMap<>();
            Map<String, Object> rawBindings = (Map<String, Object>) c.get("bindings");
            if (rawBindings != null)
                for (Map.Entry<String, Object> e : rawBindings.entrySet())
                    bindings.put(e.getKey(), bindingValue(engine, e.getValue()));
            List<HostFunction.Registration> functions = new ArrayList<>();
            List<Object> rawFns = (List<Object>) c.get("functions");
            if (rawFns != null)
                for (Object o : rawFns) {
                    Map<String, Object> f = (Map<String, Object>) o;
                    functions.add(new HostFunction.Registration((String) f.get("name"), (String) f.get("signature"),
                            HostFunctions.get((String) f.get("impl"))));
                }
            Engine.EvalOptions opts = null;
            Map<String, Object> bounds = (Map<String, Object>) c.get("bounds");
            if (bounds != null) {
                opts = new Engine.EvalOptions();
                if (bounds.get("timelimit") != null) opts.timelimit = ((Number) bounds.get("timelimit")).longValue();
                if (bounds.get("depth") != null) opts.depth = ((Number) bounds.get("depth")).intValue();
            }
            Object input = c.containsKey("input") ? c.get("input") : Values.UNDEFINED;
            phase = "compile";
            // "exprJvm": expression evaluated by the JVM side instead of "expr" (used to verify rewrites:
            // original on jsonata-js vs. rewritten on the JVM engine)
            String expr = (String) (c.get("exprJvm") != null ? c.get("exprJvm") : c.get("expr"));
            Engine.Compiled compiled = engine.compile(expr, (Map<String, Object>) c.get("options"));
            phase = "evaluate";
            result = compiled.evaluate(input, bindings, functions, opts);
        } catch (EngineError e) {
            error = e;
        } catch (Throwable t) {
            error = new EngineError(null, null, null, String.valueOf(t), true, t.getClass().getName(), t);
        }
        long micros = (System.nanoTime() - t0) / 1000;

        b.append("{\"id\":");
        Json.quote(id, b);
        b.append(",\"engine\":");
        Json.quote(engine.name(), b);
        if (error == null) {
            b.append(",\"status\":\"ok\",\"result\":");
            Json.writeCanon(result, b);
        } else {
            b.append(",\"status\":\"").append(error.crash ? "crash" : "error").append("\",\"phase\":\"").append(phase)
                    .append("\",\"error\":{\"code\":");
            if (error.code == null) b.append("null"); else Json.quote(error.code, b);
            b.append(",\"position\":").append(error.position == null ? "null" : error.position.toString());
            b.append(",\"token\":");
            if (error.token == null) b.append("null"); else Json.quote(String.valueOf(error.token), b);
            b.append(",\"message\":");
            Json.quote(String.valueOf(error.getMessage()), b);
            b.append(",\"exception\":");
            Json.quote(String.valueOf(error.exceptionClass), b);
            if (error.crash && error.getCause() != null) {
                StackTraceElement[] st = error.getCause().getStackTrace();
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < Math.min(6, st.length); k++) sb.append(st[k]).append(" | ");
                b.append(",\"stack\":");
                Json.quote(sb.toString(), b);
            }
            b.append('}');
        }
        b.append(",\"us\":").append(micros).append('}');
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    static Object bindingValue(Engine engine, Object v) throws EngineError {
        if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            if (m.size() == 1 && m.get("$expr") instanceof String)
                return engine.compile((String) m.get("$expr")).evaluate(Values.UNDEFINED, null, null, null);
            if (m.size() == 1 && m.get("$host") instanceof String)
                return HostFunctions.get((String) m.get("$host"));
            if (m.size() == 1 && m.containsKey("$undefined"))
                return Values.UNDEFINED;
        }
        return v;
    }
}
