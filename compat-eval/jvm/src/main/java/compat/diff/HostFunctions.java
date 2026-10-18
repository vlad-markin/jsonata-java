package compat.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import compat.engine.HostFunction;
import compat.engine.Values;

/**
 * Registry of host functions with identical semantics in diff-runner/js/hostfns.js.
 * Cases refer to them by name ("impl").
 */
public final class HostFunctions {
    private HostFunctions() {}

    public static HostFunction get(String impl) {
        switch (impl) {
            case "echo": return (a, c) -> arg(a, 0);
            case "upper": return (a, c) -> {
                Object s = arg(a, 0);
                return s instanceof String ? ((String) s).toUpperCase() : Values.UNDEFINED;
            };
            case "concat": return (a, c) -> str(arg(a, 0)) + str(arg(a, 1));
            case "add": return (a, c) -> ((Number) arg(a, 0)).doubleValue() + ((Number) arg(a, 1)).doubleValue();
            case "typeOf": return (a, c) -> typeOf(arg(a, 0));
            case "argCount": return (a, c) -> (long) a.size();
            case "argTypes": return (a, c) -> {
                List<Object> r = new ArrayList<>();
                for (Object o : a) r.add(typeOf(o));
                return r;
            };
            // blocking in Java, Promise-returning in JS
            case "asyncEcho": return (a, c) -> { Thread.sleep(1); return arg(a, 0); };
            case "asyncUpper": return (a, c) -> {
                Thread.sleep(1);
                Object s = arg(a, 0);
                return s instanceof String ? ((String) s).toUpperCase() : Values.UNDEFINED;
            };
            // host calls a lambda passed as argument
            case "apply": return (a, c) -> c.callFunction(arg(a, 0), list(arg(a, 1)));
            case "apply2": return (a, c) -> c.callFunction(arg(a, 0), list(arg(a, 1), arg(a, 2)));
            case "mapAll": return (a, c) -> {
                List<Object> r = new ArrayList<>();
                Object arr = arg(a, 1);
                if (arr instanceof List)
                    for (Object o : (List<?>) arr) r.add(c.callFunction(arg(a, 0), list(o)));
                return r;
            };
            case "contextInput": return (a, c) -> c.input();
            case "throwError": return (a, c) -> { throw new HostException(str(arg(a, 0))); };
            case "makeObject": return (a, c) -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("z", 1L); m.put("a", 2L); m.put("m", null);
                return m;
            };
            default: throw new IllegalArgumentException("unknown host function " + impl);
        }
    }

    public static final class HostException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public HostException(String m) { super(m); }
    }

    static Object arg(List<Object> a, int i) {
        return i < a.size() ? a.get(i) : Values.UNDEFINED;
    }

    static List<Object> list(Object... o) {
        List<Object> l = new ArrayList<>();
        for (Object x : o) l.add(x);
        return l;
    }

    static String str(Object o) {
        if (o == Values.UNDEFINED) return "undefined";
        if (o instanceof Number) return Json.numberLexeme((Number) o);
        return String.valueOf(o);
    }

    static String typeOf(Object o) {
        if (o == Values.UNDEFINED) return "undefined";
        if (o == null) return "null";
        if (o instanceof String) return "string";
        if (o instanceof Number) return "number";
        if (o instanceof Boolean) return "boolean";
        if (o instanceof List) return "array";
        if (o instanceof Map) return "object";
        if (o instanceof Values.FunctionValue || o instanceof HostFunction) return "function";
        return "other:" + o.getClass().getSimpleName();
    }
}
