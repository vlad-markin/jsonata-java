package compat.engine;

import java.util.List;
import java.util.Map;

/**
 * Engine-neutral contract used by the compatibility tools (diff-runner, benchmarks).
 *
 * <p>Value model exchanged with an engine (both directions):
 * <ul>
 *   <li>{@link Values#UNDEFINED} - JSONata "undefined" (no value)</li>
 *   <li>Java {@code null} - JSON null</li>
 *   <li>{@link String}, {@link Boolean}</li>
 *   <li>{@link Number} - any subclass; engines should return what they natively produce, the
 *       diff-runner reports the Java type separately</li>
 *   <li>{@link java.util.Map} (insertion ordered) and {@link java.util.List};
 *       a result list may be a {@link Values.Seq} to signal a JSONata sequence</li>
 *   <li>{@link HostFunction} - a function implemented by the host</li>
 *   <li>anything else - an opaque engine value (e.g. a lambda), which may be passed back to the
 *       same engine via bindings or host-function calls</li>
 * </ul>
 * To plug in another JVM engine implement this interface and pass its class name to the runner
 * ({@code -Dengine=my.Engine}) or register it via {@link java.util.ServiceLoader}.
 */
public interface Engine {

    /** Short identifier written into result files, e.g. "dashjoin-0.9.10+fork". */
    String name();

    /** Compiles an expression. Syntax errors are reported as {@link EngineError}. */
    default Compiled compile(String expression) throws EngineError {
        return compile(expression, null);
    }

    /**
     * Compiles an expression with jsonata-js style options ({@code timeout}, {@code stack},
     * {@code sequence} guardrails of jsonata-js 2.2). Unsupported options should be ignored.
     */
    Compiled compile(String expression, java.util.Map<String, Object> options) throws EngineError;

    interface Compiled {
        /**
         * Evaluates the compiled expression. Must be callable repeatedly and, if the engine claims
         * thread safety, concurrently.
         *
         * @param input    input document in the neutral model ({@link Values#UNDEFINED} = no input)
         * @param bindings variable bindings (name without '$'); values may be {@link HostFunction}s
         * @param functions host functions to register with a signature (like jsonata-js registerFunction)
         * @param options  evaluation options, may be null
         */
        Object evaluate(Object input, Map<String, Object> bindings, List<HostFunction.Registration> functions,
                        EvalOptions options) throws EngineError;
    }

    /** Options mirroring the jsonata-js test-suite "timelimit"/"depth" fields. */
    final class EvalOptions {
        /** Time limit in ms (engine runtime bound), or null. */
        public Long timelimit;
        /** Max evaluation depth (engine runtime bound), or null. */
        public Integer depth;
    }
}
