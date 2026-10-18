package com.dashjoin.jsonata.debug;

/** Events delivered by {@link DebugSession#nextEvent(long)}. */
public abstract class DebugEvent {

    public enum StopReason { ENTRY, BREAKPOINT, STEP, PAUSE, EXCEPTION }

    /** where the evaluation is suspended relative to the node */
    public enum Phase {
        /** before the node is evaluated */
        ENTER,
        /** after the node was evaluated; {@link Stopped#result} holds its value */
        EXIT,
        /** the node's evaluation threw; {@link Stopped#error} holds the exception */
        ERROR
    }

    /** the evaluation thread is suspended */
    public static final class Stopped extends DebugEvent {
        public final StopReason reason;
        public final Phase phase;
        public final NodeInfo node;
        /** breakpoint that caused the stop (null otherwise) */
        public final Breakpoint breakpoint;
        public final Object result;
        public final Throwable error;
        /** evaluation depth (number of nested node evaluations, 1 = root) */
        public final int depth;
        /** number of call frames (1 = only the main frame) */
        public final int frames;

        Stopped(StopReason reason, Phase phase, NodeInfo node, Breakpoint bp, Object result, Throwable error,
                int depth, int frames) {
            this.reason = reason; this.phase = phase; this.node = node; this.breakpoint = bp;
            this.result = result; this.error = error; this.depth = depth; this.frames = frames;
        }

        @Override public String toString() {
            return "stopped(" + reason + (breakpoint != null ? " #" + breakpoint.id : "") + ", " + phase + " "
                + node.label() + " @" + node.rangeString() + ")";
        }
    }

    /** the evaluation finished (normally, with an error, or was terminated) */
    public static final class Terminated extends DebugEvent {
        public final Object result;
        public final Throwable error;
        Terminated(Object result, Throwable error) { this.result = result; this.error = error; }
        @Override public String toString() { return "terminated(" + (error != null ? error : "ok") + ")"; }
    }

    /** output of a logpoint or a diagnostic */
    public static final class Output extends DebugEvent {
        public final String text;
        public final NodeInfo node;
        Output(String text, NodeInfo node) { this.text = text; this.node = node; }
        @Override public String toString() { return "output(" + text + ")"; }
    }
}
