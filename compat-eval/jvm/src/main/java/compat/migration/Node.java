package compat.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin view over a jsonata-js {@code ast()} node (parsed JSON). Works with any parser that
 * produces the same format (e.g. our Kotlin parser). Positions follow jsonata-js: {@code position}
 * is the character index AFTER the token; there is no start position.
 */
public final class Node {
    public final Map<String, Object> m;
    public final Node parent;
    /** role of this node in its parent (e.g. "arguments[2]", "lhs", "steps[0]") */
    public final String role;

    Node(Map<String, Object> m, Node parent, String role) {
        this.m = m;
        this.parent = parent;
        this.role = role;
    }

    public String type() { return str("type"); }
    public Object value() { return m.get("value"); }
    public String str(String k) { Object v = m.get(k); return v instanceof String ? (String) v : null; }
    public Integer position() { Object p = m.get("position"); return p instanceof Number ? ((Number) p).intValue() : null; }

    @SuppressWarnings("unchecked")
    public Node child(String k) {
        Object v = m.get(k);
        return v instanceof Map ? new Node((Map<String, Object>) v, this, k) : null;
    }

    @SuppressWarnings("unchecked")
    public List<Node> list(String k) {
        List<Node> r = new ArrayList<>();
        Object v = m.get(k);
        if (v instanceof List) {
            int i = 0;
            for (Object o : (List<Object>) v) {
                if (o instanceof Map) r.add(new Node((Map<String, Object>) o, this, k + "[" + i + "]"));
                i++;
            }
        }
        return r;
    }

    /** key/value pairs of an object constructor ({@code lhs} of a unary "{") or a group */
    @SuppressWarnings("unchecked")
    public List<Node[]> pairs() {
        List<Node[]> r = new ArrayList<>();
        Object v = m.get("lhs");
        if (v instanceof List) {
            int i = 0;
            for (Object o : (List<Object>) v) {
                if (o instanceof List && ((List<Object>) o).size() == 2) {
                    List<Object> p = (List<Object>) o;
                    r.add(new Node[] {
                        p.get(0) instanceof Map ? new Node((Map<String, Object>) p.get(0), this, "lhs[" + i + "][0]") : null,
                        p.get(1) instanceof Map ? new Node((Map<String, Object>) p.get(1), this, "lhs[" + i + "][1]") : null });
                }
                i++;
            }
        }
        return r;
    }

    /** name of the called function if this is a call of a variable ($name(...)), else null */
    public String functionName() {
        if (!"function".equals(type()) && !"partial".equals(type())) return null;
        Node p = child("procedure");
        return p != null && "variable".equals(p.type()) ? p.str("value") : null;
    }

    public List<Node> arguments() { return list("arguments"); }

    /** all nodes of the tree (pre-order) */
    @SuppressWarnings("unchecked")
    public static void walk(Node n, java.util.function.Consumer<Node> f) {
        f.accept(n);
        for (Map.Entry<String, Object> e : n.m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) walk(new Node((Map<String, Object>) v, n, e.getKey()), f);
            else if (v instanceof List) walkList((List<Object>) v, n, e.getKey(), f);
        }
    }

    @SuppressWarnings("unchecked")
    private static void walkList(List<Object> l, Node parent, String key, java.util.function.Consumer<Node> f) {
        int i = 0;
        for (Object o : l) {
            if (o instanceof Map) walk(new Node((Map<String, Object>) o, parent, key + "[" + i + "]"), f);
            else if (o instanceof List) walkList((List<Object>) o, parent, key + "[" + i + "]", f);
            i++;
        }
    }
}
