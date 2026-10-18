package com.dashjoin.jsonata.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dashjoin.jsonata.DebugAccess;
import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Parser.Symbol;

/**
 * Index over the processed AST of one expression: node ids, ranges, parent/children,
 * position lookup ("innermost node at line:column") and breakpoint location resolution.
 */
public final class AstIndex {

    private final SourceText source;
    private final List<NodeInfo> nodes = new ArrayList<>();
    private final Map<Symbol, NodeInfo> bySymbol = new IdentityHashMap<>();
    /** lambda body node -> lambda node */
    private final Map<Symbol, NodeInfo> lambdaOfBody = new IdentityHashMap<>();
    /** procedure expression of a tail call (thunk.body.procedure) -> thunk node */
    private final Map<Symbol, NodeInfo> tailCallProcedures = new IdentityHashMap<>();
    /** nodes that are never passed to the evaluator's per-node entry point */
    private final Set<NodeInfo> notExecutable = Collections.newSetFromMap(new IdentityHashMap<>());
    private final NodeInfo root;

    public AstIndex(Jsonata expression, String sourceText) {
        this(expression.getAst(), sourceText);
    }

    public AstIndex(Symbol ast, String sourceText) {
        this.source = new SourceText(sourceText);
        this.root = build(ast, null, null);
    }

    private NodeInfo build(Symbol s, NodeInfo parent, String role) {
        NodeInfo existing = bySymbol.get(s);
        if (existing != null) return null; // shared node, index once
        String kind = DebugAccess.kind(s);
        NodeInfo n = new NodeInfo(nodes.size(), s, kind, displayValue(s, kind), role,
                s.getStart(), s.getEnd(), source, parent);
        nodes.add(n);
        bySymbol.put(s, n);
        if (parent != null) parent.children.add(n);
        switch (kind) {
            case "filter": case "sort": case "index": case "group":
                notExecutable.add(n);
                break;
            default:
        }
        if ("param".equals(role)) notExecutable.add(n);
        if (parent != null && "body".equals(role) && DebugAccess.isThunk(parent.symbol)) {
            // the tail call itself is never evaluated as a node: the trampoline in apply()
            // evaluates its procedure and arguments separately
            notExecutable.add(n);
            Symbol proc = DebugAccess.procedureOf(s);
            if (proc != null) tailCallProcedures.put(proc, parent);
        }
        if (DebugAccess.isLambda(s) && DebugAccess.lambdaBody(s) != null)
            lambdaOfBody.put(DebugAccess.lambdaBody(s), n);
        for (DebugAccess.Child c : DebugAccess.children(s))
            build(c.node, n, c.role);
        return n;
    }

    private static Object displayValue(Symbol s, String kind) {
        switch (kind) {
            case "path": case "block": case "lambda": case "condition": case "transform":
            case "filter": case "sort": case "group": case "?":
                return null;
            case "function": case "partial": {
                Symbol p = DebugAccess.procedureOf(s);
                if (p != null && "variable".equals(p.getType())) return "$" + p.getValue();
                return null;
            }
            case "variable":
                return "$" + s.getValue();
            default:
                return s.getValue();
        }
    }

    public SourceText source() { return source; }
    public NodeInfo root() { return root; }
    public List<NodeInfo> nodes() { return Collections.unmodifiableList(nodes); }
    public NodeInfo node(int id) { return nodes.get(id); }

    /** node info for an engine node, null if the node does not belong to this AST (e.g. $eval) */
    public NodeInfo get(Symbol s) { return bySymbol.get(s); }

    /** lambda node whose body is the given node, or null */
    public NodeInfo lambdaOfBody(Symbol body) { return lambdaOfBody.get(body); }

    /** thunk node if the node is the procedure expression of a tail call */
    public NodeInfo tailCallOf(Symbol procedure) { return tailCallProcedures.get(procedure); }

    public boolean isExecutable(NodeInfo n) { return n != null && !notExecutable.contains(n); }

    /** innermost node whose range contains the offset (start <= offset < end); null if none */
    public NodeInfo nodeAt(int offset) {
        NodeInfo best = innermost(offset);
        if (best == null && offset > 0) best = innermost(offset - 1); // caret right after a token
        return best;
    }

    /** innermost node at a 1-based line / column */
    public NodeInfo nodeAt(int line, int column) {
        return nodeAt(source.offset(line, column));
    }

    private NodeInfo innermost(int offset) {
        NodeInfo best = null;
        for (NodeInfo n : nodes) {
            if (!n.hasRange() || offset < n.start || offset >= n.end) continue;
            if (best == null) { best = n; continue; }
            int len = n.end - n.start, bestLen = best.end - best.start;
            if (len < bestLen || (len == bestLen && n.depth > best.depth)) best = n;
        }
        return best;
    }

    /**
     * Maps a node chosen by position to the node(s) a breakpoint is attached to:
     * non-executable nodes are redirected (tail call -> its thunk, filter/sort/group/index ->
     * first executable descendant, lambda parameter -> lambda); nodes duplicated by the parser
     * ({@code ?:} and {@code ??} clone their left operand) are all included.
     */
    public List<NodeInfo> breakpointTargets(NodeInfo n) {
        NodeInfo t = n;
        while (t != null && !isExecutable(t)) {
            if ("body".equals(t.role) && t.parent != null && DebugAccess.isThunk(t.parent.symbol)) {
                t = t.parent;
            } else if ("param".equals(t.role)) {
                t = t.parent;
            } else {
                NodeInfo d = firstExecutableDescendant(t);
                t = d != null ? d : t.parent;
            }
        }
        if (t == null) return Collections.emptyList();
        List<NodeInfo> res = new ArrayList<>();
        res.add(t);
        if (t.hasRange()) for (NodeInfo o : nodes)
            if (o != t && o.start == t.start && o.end == t.end && o.kind.equals(t.kind) && isExecutable(o)
                    && (o.value == null ? t.value == null : o.value.equals(t.value)))
                res.add(o);
        return res;
    }

    private NodeInfo firstExecutableDescendant(NodeInfo n) {
        for (NodeInfo c : n.children) {
            if (isExecutable(c)) return c;
            NodeInfo d = firstExecutableDescendant(c);
            if (d != null) return d;
        }
        return null;
    }

    /** breakpoint location for a whole line: the outermost executable node starting first on that line */
    public NodeInfo firstNodeOnLine(int line) {
        NodeInfo best = null;
        for (NodeInfo n : nodes) {
            if (!n.hasRange() || n.startLine != line || !isExecutable(n)) continue;
            if (best == null || n.start < best.start
                    || (n.start == best.start && (n.end - n.start) > (best.end - best.start)))
                best = n;
        }
        return best;
    }

    /** debug dump of the tree: one node per line, indented */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        dump(root, sb);
        return sb.toString();
    }

    private void dump(NodeInfo n, StringBuilder sb) {
        if (n == null) return;
        sb.append("  ".repeat(n.depth)).append(n.role == null ? "" : n.role + ": ").append(n.label())
          .append(" [").append(n.start).append(",").append(n.end).append(") ")
          .append(n.hasRange() ? "'" + source.text().substring(n.start, n.end).replace("\n", "\\n") + "'" : "")
          .append(isExecutable(n) ? "" : " (not evaluated directly)").append('\n');
        for (NodeInfo c : n.children) dump(c, sb);
    }
}
