package compat.engine;

import java.util.ArrayList;
import java.util.Collection;

/** Shared constants of the neutral value model. */
public final class Values {
    private Values() {}

    /** JSONata undefined. */
    public static final Object UNDEFINED = new Object() {
        @Override public String toString() { return "undefined"; }
    };

    /** A list that the engine flagged as a JSONata sequence. */
    public static final class Seq extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;
        public Seq() { super(); }
        public Seq(Collection<?> c) { super(c); }
    }

    /** Opaque function value returned by an engine (lambda, native function...). */
    public static final class FunctionValue {
        public final String kind;
        public final Object engineValue;
        public FunctionValue(String kind, Object engineValue) { this.kind = kind; this.engineValue = engineValue; }
        @Override public String toString() { return "function:" + kind; }
    }
}
