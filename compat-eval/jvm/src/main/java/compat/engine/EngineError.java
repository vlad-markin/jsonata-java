package compat.engine;

/** Normalized engine error: either a JSONata error (code set) or a crash (unexpected exception). */
public class EngineError extends Exception {
    private static final long serialVersionUID = 1L;

    /** JSONata error code (e.g. "T2001"), null if the engine did not report one. */
    public final String code;
    /** Character position as reported by the engine (jsonata-js semantics: index after the token), or null. */
    public final Integer position;
    /** Offending token, if reported. */
    public final Object token;
    /** True if the failure was not a JSONata error but an unexpected exception (NPE, ClassCastException...). */
    public final boolean crash;
    /** Class name of the underlying exception. */
    public final String exceptionClass;

    public EngineError(String code, Integer position, Object token, String message, boolean crash,
                       String exceptionClass, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.position = position;
        this.token = token;
        this.crash = crash;
        this.exceptionClass = exceptionClass;
    }
}
