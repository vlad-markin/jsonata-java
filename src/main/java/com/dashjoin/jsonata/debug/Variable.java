package com.dashjoin.jsonata.debug;

import com.dashjoin.jsonata.DebugAccess;

/** A named value in a {@link Scope}. Names use JSONata syntax: {@code $x}, {@code $}, {@code $$}, {@code @}. */
public final class Variable {
    public final String name;
    /** raw engine value (undefined = null) */
    public final Object value;
    /** true if a binding with a nearer scope hides this one */
    public final boolean shadowed;

    Variable(String name, Object value, boolean shadowed) {
        this.name = name; this.value = value; this.shadowed = shadowed;
    }

    /** JSON-like rendering for display */
    public String display() { return DebugAccess.render(value); }

    @Override public String toString() { return name + "=" + display() + (shadowed ? " (shadowed)" : ""); }
}
