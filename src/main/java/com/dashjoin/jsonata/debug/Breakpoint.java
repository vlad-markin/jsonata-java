package com.dashjoin.jsonata.debug;

import java.util.Collections;
import java.util.List;

import com.dashjoin.jsonata.Jsonata;

/**
 * A breakpoint bound to one AST node (plus parser duplicates of it, see
 * {@link AstIndex#breakpointTargets(NodeInfo)}). It is hit every time the node is entered,
 * i.e. once per element for a path step.
 * <p>
 * {@code condition}: JSONata expression evaluated in the scope of the node (truthy = stop).<br>
 * {@code hitCondition}: {@code n} or {@code >=n} (from the n-th hit on), {@code ==n} (only the n-th),
 * {@code >n}, {@code %n} (every n-th). Hits are counted after the condition.<br>
 * {@code logMessage}: if set, the breakpoint is a logpoint: the message is emitted
 * ({@code {expr}} parts are evaluated) and execution continues.
 */
public final class Breakpoint {
    public final int id;
    public final NodeInfo node;
    final List<NodeInfo> targets;
    public final String condition;
    public final String hitCondition;
    public final String logMessage;
    final Jsonata conditionExpr;
    volatile boolean enabled = true;
    int hits;

    Breakpoint(int id, NodeInfo node, List<NodeInfo> targets, String condition, String hitCondition,
               String logMessage, Jsonata conditionExpr) {
        this.id = id; this.node = node; this.targets = targets; this.condition = condition;
        this.hitCondition = hitCondition; this.logMessage = logMessage; this.conditionExpr = conditionExpr;
        if (hitCondition != null) hitMatches(1); // validate syntax early
    }

    public List<NodeInfo> targets() { return Collections.unmodifiableList(targets); }
    public int hits() { return hits; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { enabled = e; }

    boolean hitMatches(int count) {
        if (hitCondition == null || hitCondition.isBlank()) return true;
        String h = hitCondition.trim();
        try {
            if (h.startsWith(">=")) return count >= Integer.parseInt(h.substring(2).trim());
            if (h.startsWith("==")) return count == Integer.parseInt(h.substring(2).trim());
            if (h.startsWith(">")) return count > Integer.parseInt(h.substring(1).trim());
            if (h.startsWith("%")) return count % Integer.parseInt(h.substring(1).trim()) == 0;
            return count >= Integer.parseInt(h);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid hit condition: " + hitCondition);
        }
    }

    @Override public String toString() {
        return "#" + id + " at " + node.rangeString() + " " + node.label()
            + (condition != null ? " if " + condition : "")
            + (hitCondition != null ? " hit " + hitCondition : "")
            + (logMessage != null ? " log \"" + logMessage + "\"" : "")
            + (enabled ? "" : " (disabled)") + " hits=" + hits;
    }
}
