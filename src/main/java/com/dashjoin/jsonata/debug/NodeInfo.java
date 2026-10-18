package com.dashjoin.jsonata.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.dashjoin.jsonata.Parser.Symbol;

/**
 * Engine-independent description of one AST node: stable id (pre-order index),
 * kind (jsonata-js AST type), source range and tree links.
 */
public final class NodeInfo {
    /** pre-order index in the processed AST, stable for a given source text */
    public final int id;
    /** jsonata-js AST type: path, name, binary, function, lambda, filter, sort, ... */
    public final String kind;
    /** operator / name / literal value (may be null) */
    public final Object value;
    /** role in the parent: step, lhs, rhs, argument, body, predicate, ... (null for the root) */
    public final String role;
    /** [start, end) offsets into the source; -1 if the node is synthetic and has no range */
    public final int start, end;
    public final int startLine, startColumn, endLine, endColumn;
    public final NodeInfo parent;
    final List<NodeInfo> children = new ArrayList<>();
    /** depth in the AST (root = 0) */
    public final int depth;
    final transient Symbol symbol;

    NodeInfo(int id, Symbol symbol, String kind, Object value, String role, int start, int end,
             SourceText src, NodeInfo parent) {
        this.id = id; this.symbol = symbol; this.kind = kind; this.value = value; this.role = role;
        this.start = start; this.end = end; this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
        if (start >= 0) {
            startLine = src.line(start); startColumn = src.column(start);
            endLine = src.line(end); endColumn = src.column(end);
        } else {
            startLine = startColumn = endLine = endColumn = -1;
        }
    }

    public List<NodeInfo> children() { return Collections.unmodifiableList(children); }

    public boolean hasRange() { return start >= 0; }

    /** the engine node (only meaningful for the engine that produced it) */
    public Symbol symbol() { return symbol; }

    /** "L:C-L:C" with an exclusive end column */
    public String rangeString() {
        if (!hasRange()) return "?";
        return startLine + ":" + startColumn + "-" + endLine + ":" + endColumn;
    }

    /** short label, e.g. {@code name "Order"} or {@code binary "+"} */
    public String label() {
        return value == null ? kind : kind + " \"" + value + "\"";
    }

    @Override public String toString() {
        return "#" + id + " " + label() + " @" + rangeString();
    }
}
