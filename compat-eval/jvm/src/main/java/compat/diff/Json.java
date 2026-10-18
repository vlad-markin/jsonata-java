package compat.diff;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import compat.engine.HostFunction;
import compat.engine.Values;

/**
 * Minimal JSON reader/writer used by the tools (no dependencies).
 * <ul>
 * <li>Objects keep key order (LinkedHashMap).</li>
 * <li>Integer literals that fit into long become Long, all other numbers Double
 *     (comparable to a typical Jackson setup with USE_LONG_FOR_INTS).</li>
 * </ul>
 * The writer produces either plain JSON or the "canonical typed tree" (see diff-runner/README.md).
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length())
            throw p.err("trailing characters");
        return v;
    }

    private RuntimeException err(String m) {
        return new IllegalArgumentException("JSON " + m + " at " + i);
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++; else break;
        }
    }

    private Object value() {
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    if (s.charAt(i++) != ':') throw err("expected :");
                    ws();
                    m.put(k, value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                    if (d != ',') throw err("expected , or }");
                }
            }
            case '[': {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    ws();
                    l.add(value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                    if (d != ',') throw err("expected , or ]");
                }
            }
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private void expect(String w) {
        if (!s.startsWith(w, i)) throw err("expected " + w);
        i += w.length();
    }

    private Object number() {
        int st = i;
        boolean integral = true;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+') i++;
            else if (c == '.' || c == 'e' || c == 'E') { integral = false; i++; }
            else break;
        }
        String lex = s.substring(st, i);
        if (lex.isEmpty()) throw err("unexpected character");
        if (integral) {
            try {
                return Long.parseLong(lex);
            } catch (NumberFormatException e) {
                return Double.parseDouble(lex);
            }
        }
        return Double.parseDouble(lex);
    }

    private String string() {
        if (s.charAt(i) != '"') throw err("expected string");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
    }

    // ---------------------------------------------------------------- writing

    /** Writes a JSON string literal; all non-ASCII chars are escaped as UTF-16 code units. */
    public static void quote(String str, StringBuilder b) {
        b.append('"');
        for (int k = 0; k < str.length(); k++) {
            char c = str.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7e) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    /** Plain JSON (for files we write ourselves). Values must be JSON-like. */
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        writePlain(v, b);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writePlain(Object v, StringBuilder b) {
        if (v == null || v == Values.UNDEFINED) b.append("null");
        else if (v instanceof String) quote((String) v, b);
        else if (v instanceof Boolean) b.append(v);
        else if (v instanceof Number) b.append(numberLexeme((Number) v));
        else if (v instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(e.getKey(), b);
                b.append(':');
                writePlain(e.getValue(), b);
            }
            b.append('}');
        } else if (v instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object o : (List<Object>) v) {
                if (!first) b.append(',');
                first = false;
                writePlain(o, b);
            }
            b.append(']');
        } else quote(String.valueOf(v), b);
    }

    /**
     * Canonical typed tree:
     * null | true | false | "string" | ["n", lexeme, javaType] | ["a", [..]] | ["s", [..]] (sequence)
     * | ["o", [[key, value], ..]] | ["u"] (undefined) | ["f", kind] | ["x", className]
     */
    @SuppressWarnings("unchecked")
    public static void writeCanon(Object v, StringBuilder b) {
        if (v == Values.UNDEFINED) b.append("[\"u\"]");
        else if (v == null) b.append("null");
        else if (v instanceof String) quote((String) v, b);
        else if (v instanceof Boolean) b.append(v);
        else if (v instanceof Number) {
            b.append("[\"n\",\"").append(numberLexeme((Number) v)).append("\",\"").append(javaType((Number) v))
                    .append("\"]");
        } else if (v instanceof Map) {
            b.append("[\"o\",[");
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('[');
                quote(e.getKey(), b);
                b.append(',');
                writeCanon(e.getValue(), b);
                b.append(']');
            }
            b.append("]]");
        } else if (v instanceof List) {
            b.append(v instanceof Values.Seq ? "[\"s\",[" : "[\"a\",[");
            boolean first = true;
            for (Object o : (List<Object>) v) {
                if (!first) b.append(',');
                first = false;
                writeCanon(o, b);
            }
            b.append("]]");
        } else if (v instanceof Values.FunctionValue) {
            b.append("[\"f\",");
            quote(((Values.FunctionValue) v).kind, b);
            b.append(']');
        } else if (v instanceof HostFunction) {
            b.append("[\"f\",\"host\"]");
        } else {
            b.append("[\"x\",");
            quote(v.getClass().getName(), b);
            b.append(']');
        }
    }

    static String javaType(Number n) {
        if (n instanceof Long) return "L";
        if (n instanceof Integer) return "I";
        if (n instanceof Double) return "D";
        if (n instanceof Float) return "F";
        if (n instanceof BigDecimal) return "BD";
        if (n instanceof BigInteger) return "BI";
        return n.getClass().getSimpleName();
    }

    /** Number formatted exactly like ECMAScript Number.prototype.toString (with "-0" for negative zero). */
    public static String numberLexeme(Number n) {
        if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte
                || n instanceof BigInteger)
            return n.toString();
        if (n instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) n;
            double d = bd.doubleValue();
            if (!Double.isInfinite(d) && new BigDecimal(d).compareTo(bd) == 0)
                return jsDouble(d);
            return bd.stripTrailingZeros().toPlainString();
        }
        return jsDouble(n.doubleValue());
    }

    public static String jsDouble(double d) {
        if (Double.isNaN(d)) return "NaN";
        if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
        if (d == 0) return (1 / d < 0) ? "-0" : "0";
        String sign = d < 0 ? "-" : "";
        // JDK >= 19: Double.toString yields the shortest uniquely-identifying decimal
        BigDecimal bd = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        if (bd.precision() == 2) {
            // Double.toString prints at least two digits in E notation (4.9E-324); JS prints 5e-324
            BigDecimal one = bd.round(new java.math.MathContext(1, java.math.RoundingMode.HALF_EVEN));
            if (one.doubleValue() == Math.abs(d)) bd = one.stripTrailingZeros();
        }
        String digits = bd.unscaledValue().toString();
        int k = digits.length();
        int n = k - bd.scale();
        StringBuilder b = new StringBuilder(sign);
        if (k <= n && n <= 21) {
            b.append(digits);
            for (int z = 0; z < n - k; z++) b.append('0');
        } else if (0 < n && n <= 21) {
            b.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            b.append("0.");
            for (int z = 0; z < -n; z++) b.append('0');
            b.append(digits);
        } else {
            int e = n - 1;
            b.append(digits.charAt(0));
            if (k > 1) b.append('.').append(digits, 1, k);
            b.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
        }
        return b.toString();
    }
}
