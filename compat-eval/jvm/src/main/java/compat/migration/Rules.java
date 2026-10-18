package compat.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detectors for constructs whose result differs between jsonata-js 2.2.2 and the JVM engine.
 * Rule ids are the ids of compat-eval/divergences.md. Rules marked fixedInFork describe
 * divergences fixed in this fork; they are reported only with --all (useful for other engines,
 * e.g. upstream dashjoin or a new engine, and as a regression net).
 */
public final class Rules {
    private Rules() {}

    public interface Rule {
        String id();
        boolean fixedInFork();
        void check(Node root, String src, List<Finding> out);
    }

    static abstract class Base implements Rule {
        final String id;
        final boolean fixed;
        Base(String id, boolean fixed) { this.id = id; this.fixed = fixed; }
        public String id() { return id; }
        public boolean fixedInFork() { return fixed; }
        Finding f(String severity, Integer pos, String msg, String rec) {
            return new Finding(id, severity, pos, msg, rec, fixed);
        }
    }

    static boolean isString(Node n) { return n != null && "string".equals(n.type()) && n.value() instanceof String; }
    static boolean isNumber(Node n) { return n != null && "number".equals(n.type()) && n.value() instanceof Number; }

    public static List<Rule> all() {
        List<Rule> r = new ArrayList<>();

        // ---------------------------------------------------------------- not fixed in the fork
        r.add(new Base("DT-05", false) {
            final Pattern NEG = Pattern.compile("^-(\\d\\d)(\\d\\d)$");
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    String fn = n.functionName();
                    int tzArg = "fromMillis".equals(fn) ? 2 : "now".equals(fn) ? 1 : -1;
                    if (tzArg < 0 || n.arguments().size() <= tzArg) return;
                    Node tz = n.arguments().get(tzArg);
                    if (!isString(tz)) return;
                    var m = NEG.matcher((String) tz.value());
                    if (!m.matches() || m.group(2).equals("00")) return;
                    int hh = Integer.parseInt(m.group(1)) + 1;
                    String js = String.format("-%02d%s", hh, m.group(2));
                    Finding x = f("warning", tz.position(),
                        "jsonata-js 2.2.2 applies the negative offset " + tz.value() + " as " + js.substring(0, 3) + ":" + js.substring(3)
                            + " (one hour more, bug in jsonata-js); the JVM engine applies it correctly",
                        "to keep the jsonata-js result write '" + js + "' (automatic rewrite), or accept the corrected result");
                    int s = Source.stringStart(src, (String) tz.value(), tz.position());
                    if (s >= 0 && hh <= 99) x.fix(s + 1, tz.position() - 1, js);
                    out.add(x);
                });
            }
        });
        r.add(new Base("ORD-01", false) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    boolean ctor = "unary".equals(n.type()) && "{".equals(n.value());
                    boolean group = "group".equals(n.role) || n.role != null && n.role.endsWith("group");
                    if (!ctor && !group) return;
                    List<String> keys = new ArrayList<>();
                    boolean dynamic = false;
                    for (Node[] p : n.pairs()) {
                        if (isString(p[0])) keys.add((String) p[0].value());
                        else dynamic = true;
                    }
                    // literal keys: JS orders array-index keys first (ascending)
                    List<String> jsOrder = new ArrayList<>();
                    keys.stream().filter(Source::isArrayIndexKey).mapToLong(Long::parseLong).sorted().forEach(k -> jsOrder.add(Long.toString(k)));
                    keys.stream().filter(k -> !Source.isArrayIndexKey(k)).forEach(jsOrder::add);
                    if (!jsOrder.equals(keys))
                        out.add(f("warning", n.position(),
                            "object keys " + keys + ": jsonata-js orders integer-like keys first (" + jsOrder + "), the JVM engine keeps the written order",
                            "write the keys in the order " + jsOrder + " if key order matters (output, $keys, $each, $string, .*)"));
                    if (dynamic)
                        out.add(f("info", n.position(),
                            (group ? "grouping" : "object constructor") + " with computed keys: if a key is an integer-like string (\"1\", \"42\"), jsonata-js puts it first",
                            "only relevant if key order matters and keys can be numeric strings"));
                });
            }
        });
        r.add(new Base("STR-03", false) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    String fn = n.functionName();
                    if (!"base64encode".equals(fn) && !"base64decode".equals(fn)) return;
                    List<Node> a = n.arguments();
                    if (!a.isEmpty() && isString(a.get(0)) && ((String) a.get(0).value()).chars().allMatch(c -> c < 128)
                            && "base64encode".equals(fn))
                        return;
                    out.add(f("warning", n.position(),
                        "$" + fn + ": jsonata-js (Node) treats the string as Latin-1 (characters above U+00FF are truncated), the JVM engine uses UTF-8",
                        "identical for ASCII; for other text decide whether the jsonata-js result must be kept (see question STR-03)"));
                });
            }
        });
        r.add(new Base("NUM-10", false) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"binary".equals(n.type())) return;
                    Object op = n.value();
                    Node rhs = n.child("rhs");
                    if (("/".equals(op) || "%".equals(op)) && isNumber(rhs) && ((Number) rhs.value()).doubleValue() == 0)
                        out.add(f("warning", n.position(), "division by zero: jsonata-js returns Infinity/NaN, the JVM engine raises D1001",
                            "guard the divisor"));
                });
            }
        });
        r.add(new Base("NUM-11", false) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    String fn = n.functionName();
                    if ("power".equals(fn) || "sqrt".equals(fn))
                        out.add(f("info", n.position(), "$" + fn + " may differ from jsonata-js in the last bit (different libm)",
                            "use $round(..., n) if exact decimal output matters"));
                });
            }
        });
        r.add(new Base("RE-07", false) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"regex".equals(n.type()) || n.position() == null) return;
                    int s = Source.regexStart(src, n.position());
                    if (s < 0) return;
                    String re = src.substring(s, n.position());
                    if (re.codePoints().anyMatch(cp -> cp > 0xFFFF))
                        out.add(f("warning", n.position(),
                            "regex " + re + " contains characters outside the BMP (e.g. emoji): jsonata-js matches them as two UTF-16 code units, the JVM engine as one character",
                            "rewrite the regex with alternatives instead of character classes, e.g. (?:😀|x) instead of [😀x]"));
                });
            }
        });
        r.add(new Base("UND-03", false) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"regex".equals(n.type()) || n.position() == null) return;
                    int s = Source.regexStart(src, n.position());
                    if (s < 0) return;
                    String re = src.substring(s, n.position());
                    if (re.contains("(") && !re.contains("(?") && (re.contains("|") || re.matches(".*\\)[?*].*")))
                        out.add(f("info", n.position(),
                            "regex " + re + " has optional capturing groups: an unmatched group is undefined in jsonata-js and null on the JVM (groups array of $match)",
                            "identical after JSON serialisation; only relevant for $exists/$type on the group"));
                });
            }
        });
        r.add(new Base("PAD-01", false) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"pad".equals(n.functionName()) || n.arguments().size() < 2) return;
                    Node w = n.arguments().get(1);
                    if (!isNumber(w)) return;
                    if (Math.abs(((Number) w.value()).doubleValue()) > 1e7)
                        out.add(f("warning", n.position(), "$pad width above 1e7: the JVM engine raises D2016 (limit backported from jsonata-js after 2.2.2)",
                            "reduce the width"));
                });
            }
        });

        // ---------------------------------------------------------------- fixed in the fork (--all)
        r.add(new Base("API-01", true) {
            public void check(Node root, String src, List<Finding> out) {
                if ("bind".equals(root.type()))
                    out.add(f("info", root.position(), "top-level assignment: older JVM engines share it between evaluations",
                        "wrap the expression in ( ... ) or pass a fresh Frame"));
            }
        });
        r.add(new Base("NUM-01", true) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"binary".equals(n.type()) || !("=".equals(n.value()) || "!=".equals(n.value()))) return;
                    for (String side : new String[] { "lhs", "rhs" }) {
                        Node c = n.child(side);
                        if (c != null && "unary".equals(c.type()) && ("[".equals(c.value()) || "{".equals(c.value()))) {
                            out.add(f("warning", n.position(), "deep comparison with an array/object constructor depends on Java number types in unfixed JVM engines",
                                "none needed with this fork"));
                            return;
                        }
                    }
                });
            }
        });
        r.add(new Base("SORT-02", true) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"sort".equals(n.functionName()) || n.arguments().size() < 2) return;
                    Node cmp = n.arguments().get(1);
                    Node body = cmp.child("body");
                    if ("lambda".equals(cmp.type()) && body != null && "binary".equals(body.type())
                            && (">=".equals(body.value()) || "<=".equals(body.value())))
                        out.add(f("warning", n.position(), "$sort comparator with " + body.value() + " (true on ties): order depends on the sort algorithm",
                            "use a strict comparator (> or <); identical in this fork"));
                });
            }
        });
        r.add(new Base("UND-01", true) {
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (n.functionName() == null) return;
                    for (Node a : n.arguments())
                        if ("value".equals(a.type()) && a.value() == null && a.m.containsKey("value"))
                            out.add(f("warning", n.position(), "null passed to $" + n.functionName() + ": unfixed JVM engines treat it as undefined",
                                "none needed with this fork"));
                });
            }
        });
        r.add(new Base("DT-03", true) {
            final Pattern P = Pattern.compile("\\[[Xwx]");
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"fromMillis".equals(n.functionName()) && !"now".equals(n.functionName())) return;
                    for (Node a : n.arguments())
                        if (isString(a) && P.matcher((String) a.value()).find())
                            out.add(f("warning", a.position(), "picture with [X]/[w]/[x] (ISO week-year, week of month): computed differently by unfixed JVM engines",
                                "none needed once DT-03 is fixed"));
                });
            }
        });
        r.add(new Base("RE-01", true) {
            final Pattern JSONLY = Pattern.compile("\\[\\^\\]|\\\\0|\\[\\\\b\\]|\\\\[ACEFGHIJKLMNOPQRTUVXYZaeghijklmopqyz]");
            public void check(Node root, String src, List<Finding> out) {
                Node.walk(root, n -> {
                    if (!"regex".equals(n.type()) || n.position() == null) return;
                    int s = Source.regexStart(src, n.position());
                    if (s < 0) return;
                    String re = src.substring(s, n.position());
                    if (JSONLY.matcher(re).find() || re.contains("\\s") || re.matches(".*\\$/[im]*$"))
                        out.add(f("warning", n.position(), "regex " + re + " uses syntax/semantics that java.util.regex interprets differently",
                            "none needed with this fork (JS regex translation)"));
                });
            }
        });
        return r;
    }
}
