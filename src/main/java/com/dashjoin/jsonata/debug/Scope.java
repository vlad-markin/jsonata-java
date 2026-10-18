package com.dashjoin.jsonata.debug;

import java.util.Collections;
import java.util.List;

/** A group of variables shown for a stack frame (DAP "scope"). */
public final class Scope {

    public enum Kind {
        /** $ (context), $$ (root), @ (focus/tuple context) */
        CONTEXT,
        /** parameters of the lambda invocation */
        ARGUMENTS,
        /** focus (@$v), index (#$i) and ancestor bindings of a tuple stream step */
        TUPLE,
        /** variables bound by := in a block, or the closure scope of a lambda */
        LOCAL,
        /** bindings of the evaluation (evaluate(input, bindings)) */
        GLOBAL,
        /** values assigned to the expression object (Jsonata.assign), built-ins are omitted */
        ENVIRONMENT
    }

    public final Kind kind;
    public final String name;
    public final List<Variable> variables;

    Scope(Kind kind, String name, List<Variable> variables) {
        this.kind = kind; this.name = name; this.variables = Collections.unmodifiableList(variables);
    }

    public Variable get(String name) {
        for (Variable v : variables) if (v.name.equals(name)) return v;
        return null;
    }

    @Override public String toString() { return name + " " + variables; }
}
