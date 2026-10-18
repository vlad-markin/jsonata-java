package compat.engine;

import java.util.List;

/** A host (Java) function callable from JSONata. Arguments and result use the neutral value model. */
@FunctionalInterface
public interface HostFunction {

    Object call(List<Object> args, Context ctx) throws Exception;

    /** Services the engine offers to a running host function. */
    interface Context {
        /** Invokes a function value (JSONata lambda, partial application, native or host function). */
        Object callFunction(Object function, List<Object> args) throws Exception;
        /** The context value (focus) of the call, may be {@link Values#UNDEFINED}. */
        Object input();
    }

    /** A function registered with a name and an optional JSONata signature, e.g. "&lt;s:s&gt;". */
    final class Registration {
        public final String name;
        public final String signature;
        public final HostFunction function;

        public Registration(String name, String signature, HostFunction function) {
            this.name = name;
            this.signature = signature;
            this.function = function;
        }
    }
}
