package com.dashjoin.jsonata.debug;

import com.dashjoin.jsonata.Jsonata.Frame;

/** One call-stack frame (main expression or a lambda invocation), top frame first. */
public final class StackFrameInfo {
    /** 0 = innermost (top) frame */
    public final int id;
    /** e.g. {@code <main>}, {@code $f}, {@code λ via $map} */
    public final String name;
    /** lambda node of the invoked function, null for the main frame */
    public final NodeInfo lambda;
    /** call site (function / apply node) that caused the invocation, if known */
    public final NodeInfo callSite;
    /** node currently evaluated in this frame */
    public final NodeInfo node;
    /** true if this frame replaced the caller's frame by tail-call optimisation */
    public final boolean tailCall;
    /** context value ({@code $}) of {@link #node} */
    public final Object context;
    /** innermost environment of {@link #node} */
    public final Frame environment;

    StackFrameInfo(int id, String name, NodeInfo lambda, NodeInfo callSite, NodeInfo node, boolean tailCall,
                   Object context, Frame environment) {
        this.id = id; this.name = name; this.lambda = lambda; this.callSite = callSite; this.node = node;
        this.tailCall = tailCall; this.context = context; this.environment = environment;
    }

    @Override public String toString() {
        return "#" + id + " " + name + (tailCall ? " (tail call)" : "") + " at "
            + (node != null ? node.rangeString() + " " + node.label() : "?");
    }
}
