package compat.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.dashjoin.jsonata.Functions;
import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;
import com.dashjoin.jsonata.Jsonata.JFunction;
import com.dashjoin.jsonata.Parser.Symbol;
import com.dashjoin.jsonata.Utils;

/**
 * Adapter for com.dashjoin.jsonata (this fork). Uses only the public API of the engine, the same
 * way a host application would.
 */
public class DashjoinEngine implements Engine {

    @Override
    public String name() {
        return "dashjoin-fork";
    }

    @Override
    public Compiled compile(String expression, Map<String, Object> options) throws EngineError {
        Jsonata expr;
        try {
            Jsonata.Options opts = null;
            if (options != null && !options.isEmpty()) {
                opts = new Jsonata.Options();
                if (options.get("timeout") instanceof Number)
                    opts.timeout = ((Number) options.get("timeout")).longValue();
                if (options.get("stack") instanceof Number)
                    opts.stack = ((Number) options.get("stack")).intValue();
                if (options.get("sequence") instanceof Number)
                    opts.sequence = ((Number) options.get("sequence")).intValue();
            }
            expr = Jsonata.jsonata(expression, opts);
        } catch (Throwable t) {
            throw toError(t);
        }
        expr.setOutputConvertNulls(false);
        return (input, bindings, functions, evalOptions) -> {
            try {
                if (functions != null)
                    for (HostFunction.Registration r : functions)
                        expr.registerFunction(r.name, new JFunction(wrapHost(r.function), r.signature));
                Frame frame = null;
                if ((bindings != null && !bindings.isEmpty()) || evalOptions != null) {
                    frame = expr.createFrame();
                    if (bindings != null)
                        for (Map.Entry<String, Object> e : bindings.entrySet()) {
                            if (e.getValue() == Values.UNDEFINED)
                                continue;
                            frame.bind(e.getKey(), toEngine(e.getValue(), true));
                        }
                    if (evalOptions != null && evalOptions.timelimit != null && evalOptions.depth != null)
                        frame.setRuntimeBounds(evalOptions.timelimit, evalOptions.depth);
                }
                Object res = expr.evaluate(toEngine(input, true), frame);
                return fromEngine(res);
            } catch (Throwable t) {
                throw toError(t);
            }
        };
    }

    static EngineError toError(Throwable t) {
        // unwrap RuntimeException(e) wrappers created by JFunction.call
        Throwable c = t;
        while (!(c instanceof JException) && c.getCause() != null && c != c.getCause()
                && c.getClass() == RuntimeException.class)
            c = c.getCause();
        if (c instanceof JException) {
            JException je = (JException) c;
            return new EngineError(je.getError(), je.getLocation() < 0 ? null : je.getLocation(),
                    je.getCurrent() == null ? null : String.valueOf(je.getCurrent()), je.getMessage(), false,
                    je.getClass().getName(), je);
        }
        if (c instanceof EngineError)
            return (EngineError) c;
        return new EngineError(null, null, null, String.valueOf(c), true, c.getClass().getName(), c);
    }

    static Jsonata.JFunctionCallable wrapHost(HostFunction fn) {
        return (input, args) -> {
            List<Object> nargs = new ArrayList<>();
            for (Object a : args)
                nargs.add(fromEngine(a));
            Object res = fn.call(nargs, new HostFunction.Context() {
                @Override
                public Object callFunction(Object function, List<Object> fargs) throws Exception {
                    List<Object> eargs = new ArrayList<>();
                    for (Object a : fargs)
                        eargs.add(toEngine(a, true));
                    try {
                        return fromEngine(Functions.funcApply(toEngine(function, false), eargs));
                    } catch (Exception e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public Object input() {
                    return fromEngine(input);
                }
            });
            // a host returning JSON null must hand NULL_VALUE to the engine (Java null = undefined)
            return toEngine(res, true);
        };
    }

    /** neutral -> engine. Top-level JSON null becomes NULL_VALUE, nested nulls stay Java null (host style). */
    static Object toEngine(Object v, boolean topLevel) {
        if (v == Values.UNDEFINED)
            return null;
        if (v == null)
            return topLevel ? Jsonata.NULL_VALUE : null;
        if (v instanceof Values.FunctionValue)
            return ((Values.FunctionValue) v).engineValue;
        if (v instanceof HostFunction)
            return new JFunction(wrapHost((HostFunction) v), null);
        if (v instanceof Map) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
                m.put((String) e.getKey(), toEngine(e.getValue(), false));
            return m;
        }
        if (v instanceof List) {
            List<Object> l = new ArrayList<>();
            for (Object o : (List<?>) v)
                l.add(toEngine(o, false));
            return l;
        }
        return v;
    }

    /** engine -> neutral */
    static Object fromEngine(Object v) {
        if (v == null)
            return Values.UNDEFINED;
        if (v == Jsonata.NULL_VALUE)
            return null;
        if (v instanceof Map) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                Object val = e.getValue();
                // nested Java null inside engine structures = JSON null
                m.put(String.valueOf(e.getKey()), val == null ? null : fromEngine(val));
            }
            return m;
        }
        if (v instanceof List) {
            List<Object> l = Utils.isSequence(v) ? new Values.Seq() : new ArrayList<>();
            for (Object o : (List<?>) v)
                l.add(o == null ? null : fromEngine(o));
            return l;
        }
        if (v instanceof Symbol)
            return new Values.FunctionValue("lambda", v);
        if (v instanceof JFunction || v instanceof Jsonata.JLambda || v instanceof Jsonata.JFunctionCallable)
            return new Values.FunctionValue("native", v);
        if (v instanceof Pattern)
            return new Values.FunctionValue("regex", v);
        return v;
    }
}
