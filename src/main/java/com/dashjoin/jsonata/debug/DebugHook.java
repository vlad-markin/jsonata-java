package com.dashjoin.jsonata.debug;

import com.dashjoin.jsonata.Jsonata.Frame;
import com.dashjoin.jsonata.Parser.Symbol;

/**
 * Low-level debugger hook called by the evaluator for every evaluated AST node.
 * <p>
 * Attach it with {@link Frame#setDebugHook(DebugHook)} on the bindings frame passed to
 * {@link com.dashjoin.jsonata.Jsonata#evaluate(Object, Frame)}. When no hook is attached the
 * evaluator only performs one null check of a field per node.
 * <p>
 * All methods are invoked on the evaluating thread. A hook may block in {@link #enter},
 * {@link #exit} or {@link #error} to suspend the evaluation (this is how breakpoints work).
 */
public interface DebugHook {

    /** before the node is evaluated; {@code input} is the context value ({@code $}) */
    void enter(Symbol node, Object input, Frame environment);

    /** after the node has been evaluated (incl. predicates, group-by and singleton unwrapping) */
    void exit(Symbol node, Object input, Frame environment, Object result);

    /**
     * the evaluation of the node threw; called for every node on the way up,
     * innermost first. The exception is re-thrown by the evaluator afterwards.
     */
    void error(Symbol node, Object input, Frame environment, Throwable error);
}
