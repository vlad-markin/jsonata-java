package com.dashjoin.jsonata.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dashjoin.jsonata.DebugAccess;
import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;
import com.dashjoin.jsonata.Jsonata.JFunction;
import com.dashjoin.jsonata.Jsonata.JLambda;
import com.dashjoin.jsonata.Parser.Symbol;
import com.dashjoin.jsonata.debug.DebugEvent.Phase;
import com.dashjoin.jsonata.debug.DebugEvent.StopReason;
import com.dashjoin.jsonata.debug.DebugEvent.Stopped;

/**
 * A debugging session for one expression.
 * <p>
 * Threading model: {@link #start(Object)} runs the evaluation on a dedicated worker thread.
 * The controller (console, DAP adapter, test) uses the other public methods from its own thread:
 * it receives {@link DebugEvent}s via {@link #nextEvent(long)}, and while the worker is suspended
 * it may inspect the stack ({@link #stackTrace()}), scopes ({@link #scopes(int)}) and evaluate
 * expressions ({@link #evaluate(String, int)}), then resume it with
 * {@link #resume()}, {@link #stepIn()}, {@link #stepOver()}, {@link #stepOut()} or {@link #terminate()}.
 * <p>
 * Stepping semantics (d = evaluation depth of the node where the worker is suspended):
 * <ul>
 * <li>step in: stop at the next evaluated node (any depth), or after the parent finished (EXIT at depth &lt; d)</li>
 * <li>step over: stop at the next node at depth &lt;= d (the next sibling, or for a path step the same step for
 *     the next element), or after the parent finished (EXIT at depth &lt; d)</li>
 * <li>step out: inside a lambda: stop after the lambda body returned (EXIT of the body, showing the return
 *     value); in the main frame: stop after the parent expression finished</li>
 * </ul>
 * Literals are never stopped at by stepping or pausing (breakpoints may still be set on them).
 */
public final class DebugSession implements DebugHook {

    private final Jsonata expression;
    private final AstIndex index;

    // ---- configuration (controller thread) ----
    private volatile boolean stopOnError = true;
    private volatile boolean stopOnEntry = false;
    private volatile Map<NodeInfo, List<Breakpoint>> bpByNode = Collections.emptyMap();
    private final Map<Integer, Breakpoint> breakpoints = new LinkedHashMap<>();
    private int nextBreakpointId = 1;

    // ---- worker state (only touched by the worker, read by the controller while suspended) ----
    private Thread worker;
    private final ArrayList<Active> stack = new ArrayList<>();
    private final ArrayList<CallFrame> frames = new ArrayList<>();
    private boolean internal;          // evaluating a condition / logpoint: ignore notifications
    private volatile boolean terminating;
    private Throwable lastReportedError;
    private StepMode stepMode = StepMode.NONE;
    private int stepDepth;
    private boolean entryPending;
    private volatile boolean pauseRequested;

    // ---- suspension handshake ----
    private final Object lock = new Object();
    private Stopped suspended;          // non-null while the worker is suspended
    private Command command;
    private final BlockingQueue<DebugEvent> events = new LinkedBlockingQueue<>();
    private volatile boolean finished;
    private Object result;
    private Throwable failure;

    private enum StepMode { NONE, IN, OVER, OUT }
    private enum Command { RESUME, STEP_IN, STEP_OVER, STEP_OUT, TERMINATE }

    /** an active (being evaluated) node */
    private static final class Active {
        final Symbol node; final NodeInfo info; final Object input; final Frame env;
        String pendingTailCallName;    // set on a call site when the trampoline evaluated a tail call's procedure
        Active(Symbol node, NodeInfo info, Object input, Frame env) {
            this.node = node; this.info = info; this.input = input; this.env = env;
        }
    }

    /** a lambda invocation */
    private static final class CallFrame {
        final String name; final NodeInfo lambda; final NodeInfo callSite; final int base; final Frame env;
        final boolean tailCall;
        CallFrame(String name, NodeInfo lambda, NodeInfo callSite, int base, Frame env, boolean tailCall) {
            this.name = name; this.lambda = lambda; this.callSite = callSite; this.base = base; this.env = env;
            this.tailCall = tailCall;
        }
    }

    /** thrown on the worker to abort the evaluation after {@link #terminate()} */
    public static final class TerminatedByDebugger extends RuntimeException {
        private static final long serialVersionUID = 1L;
        TerminatedByDebugger() { super("evaluation terminated by debugger"); }
    }

    public DebugSession(String source) {
        this(DebugAccess.parse(source), source);
    }

    public DebugSession(Jsonata expression, String source) {
        this.expression = expression;
        this.index = new AstIndex(expression, source);
    }

    public AstIndex index() { return index; }
    public Jsonata expression() { return expression; }

    public void setStopOnError(boolean b) { stopOnError = b; }
    public void setStopOnEntry(boolean b) { stopOnEntry = b; }

    // ------------------------------------------------------------------ breakpoints

    /** breakpoint at the innermost node at line:column */
    public Breakpoint setBreakpoint(int line, int column) { return setBreakpoint(line, column, null, null, null); }

    public Breakpoint setBreakpoint(int line, int column, String condition, String hitCondition, String logMessage) {
        NodeInfo n = index.nodeAt(line, column);
        if (n == null) throw new IllegalArgumentException("no expression at " + line + ":" + column);
        return setBreakpoint(n, condition, hitCondition, logMessage);
    }

    /** breakpoint on the first expression starting on the line */
    public Breakpoint setLineBreakpoint(int line, String condition, String hitCondition, String logMessage) {
        NodeInfo n = index.firstNodeOnLine(line);
        if (n == null) throw new IllegalArgumentException("no expression starts on line " + line);
        return setBreakpoint(n, condition, hitCondition, logMessage);
    }

    public synchronized Breakpoint setBreakpoint(NodeInfo n, String condition, String hitCondition, String logMessage) {
        List<NodeInfo> targets = index.breakpointTargets(n);
        if (targets.isEmpty()) throw new IllegalArgumentException("node is not executable: " + n);
        Jsonata cond = condition == null || condition.isBlank() ? null : DebugAccess.parse(condition);
        Breakpoint bp = new Breakpoint(nextBreakpointId++, targets.get(0), targets, condition, hitCondition,
            logMessage, cond);
        breakpoints.put(bp.id, bp);
        rebuildBreakpointMap();
        return bp;
    }

    public synchronized boolean removeBreakpoint(int id) {
        boolean r = breakpoints.remove(id) != null;
        rebuildBreakpointMap();
        return r;
    }

    public synchronized void clearBreakpoints() {
        breakpoints.clear();
        rebuildBreakpointMap();
    }

    public synchronized List<Breakpoint> breakpoints() { return new ArrayList<>(breakpoints.values()); }

    private void rebuildBreakpointMap() {
        Map<NodeInfo, List<Breakpoint>> m = new IdentityHashMap<>();
        for (Breakpoint bp : breakpoints.values())
            for (NodeInfo t : bp.targets) m.computeIfAbsent(t, k -> new ArrayList<>()).add(bp);
        bpByNode = m; // copy-on-write, read by the worker
    }

    // ------------------------------------------------------------------ run control (controller thread)

    public void start(Object input) { start(input, null); }

    /** starts the evaluation on a worker thread; events are delivered via {@link #nextEvent(long)} */
    public synchronized void start(Object input, Frame bindings) {
        if (worker != null) throw new IllegalStateException("already started");
        final Frame b = bindings != null ? bindings : expression.createFrame();
        b.setDebugHook(this);
        entryPending = stopOnEntry;
        worker = new Thread(() -> {
            Object r = null;
            Throwable err = null;
            try {
                r = expression.evaluate(input, b);
            } catch (Throwable t) {
                err = t;
            }
            synchronized (lock) {
                result = r; failure = err; finished = true;
                lock.notifyAll();
            }
            events.add(new DebugEvent.Terminated(r, err));
        }, "jsonata-debuggee");
        worker.setDaemon(true);
        worker.start();
    }

    /** next event, or null on timeout */
    public DebugEvent nextEvent(long timeoutMillis) throws InterruptedException {
        return events.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** next Stopped or Terminated event (Output events are skipped and returned via the list, if given) */
    public DebugEvent nextStopOrEnd(long timeoutMillis, List<DebugEvent.Output> output) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return null;
            DebugEvent e = nextEvent(left);
            if (e == null) return null;
            if (e instanceof DebugEvent.Output) {
                if (output != null) output.add((DebugEvent.Output) e);
                continue;
            }
            return e;
        }
    }

    /** request the running evaluation to suspend at the next evaluated (non-literal) node */
    public void pause() { pauseRequested = true; }

    public void resume() { send(Command.RESUME); }
    public void stepIn() { send(Command.STEP_IN); }
    public void stepOver() { send(Command.STEP_OVER); }
    public void stepOut() { send(Command.STEP_OUT); }
    /** aborts the evaluation (it ends with {@link TerminatedByDebugger}) */
    public void terminate() {
        synchronized (lock) {
            if (suspended == null) { terminating = true; pauseRequested = true; return; }
        }
        send(Command.TERMINATE);
    }

    private void send(Command c) {
        synchronized (lock) {
            if (suspended == null) throw new IllegalStateException("evaluation is not suspended");
            command = c;
            lock.notifyAll();
        }
    }

    public boolean isSuspended() { synchronized (lock) { return suspended != null; } }
    public boolean isFinished() { return finished; }

    /** waits for the end of the evaluation and returns its result (rethrows its error) */
    public Object awaitResult(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (lock) {
            while (!finished) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) throw new IllegalStateException("evaluation did not finish");
                lock.wait(left);
            }
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
        return result;
    }

    // ------------------------------------------------------------------ inspection (while suspended)

    public Stopped stopped() { synchronized (lock) { return suspended; } }

    private void requireSuspended() {
        synchronized (lock) {
            if (suspended == null) throw new IllegalStateException("evaluation is not suspended");
        }
    }

    /** call stack, innermost frame first */
    public List<StackFrameInfo> stackTrace() {
        requireSuspended();
        List<StackFrameInfo> res = new ArrayList<>();
        for (int f = frames.size() - 1; f >= 0; f--) {
            CallFrame cf = frames.get(f);
            Active a = currentActive(f);
            res.add(new StackFrameInfo(frames.size() - 1 - f, cf.name, cf.lambda, cf.callSite,
                a != null ? a.info : null, cf.tailCall,
                a != null ? DebugAccess.contextValue(a.input) : null, a != null ? a.env : cf.env));
        }
        return res;
    }

    /** innermost active node (with a known AST node) of call frame f (0 = main) */
    private Active currentActive(int f) {
        int from = frames.get(f).base;
        int to = f + 1 < frames.size() ? frames.get(f + 1).base : stack.size();
        for (int i = to - 1; i >= from; i--)
            if (stack.get(i).info != null) return stack.get(i);
        return to > from ? stack.get(to - 1) : null;
    }

    private Active activeOfFrameId(int frameId) {
        int f = frames.size() - 1 - frameId;
        if (f < 0 || f >= frames.size()) throw new IllegalArgumentException("no frame " + frameId);
        Active a = currentActive(f);
        if (a == null) throw new IllegalStateException("frame " + frameId + " has no active node");
        return a;
    }

    /** scopes of a stack frame (0 = top): context, then the scope chain from innermost to outermost */
    public List<Scope> scopes(int frameId) {
        requireSuspended();
        Active a = activeOfFrameId(frameId);
        List<Scope> res = new ArrayList<>();
        Frame base = a.env.getBase();

        List<Variable> ctx = new ArrayList<>();
        ctx.add(new Variable("$", DebugAccess.contextValue(a.input), false));
        ctx.add(new Variable("$$", a.env.lookup("$"), false));
        for (Frame f = a.env; f != null && f != base; f = f.getParent())
            if (f.getLocalBindings().containsKey("@")) {
                ctx.add(new Variable("@", f.getLocalBindings().get("@"), false));
                break;
            }
        res.add(new Scope(Scope.Kind.CONTEXT, "Context", ctx));

        Set<Frame> argFrames = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Frame, CallFrame> frameOfEnv = new IdentityHashMap<>();
        for (CallFrame cf : frames) if (cf.env != null) { argFrames.add(cf.env); frameOfEnv.put(cf.env, cf); }

        Set<String> seen = new HashSet<>();
        boolean pastBase = false;
        int localNo = 0;
        for (Frame f = a.env; f != null; f = f.getParent()) {
            List<Variable> vars = new ArrayList<>();
            for (Map.Entry<String, Object> e : f.getLocalBindings().entrySet()) {
                String k = e.getKey();
                if (k.startsWith("__") || k.startsWith("!") || k.equals("@")) continue; // internal / ancestor slots
                if (k.equals("$")) continue;                                             // shown as $$
                Object v = e.getValue();
                if (pastBase && (v instanceof JFunction || v instanceof JLambda)) continue;  // built-ins
                vars.add(new Variable("$" + k, v, !seen.add(k)));
            }
            Scope.Kind kind;
            String name;
            if (f == base) { kind = Scope.Kind.GLOBAL; name = "Global"; }
            else if (pastBase) { kind = Scope.Kind.ENVIRONMENT; name = "Environment"; }
            else if (argFrames.contains(f)) {
                kind = Scope.Kind.ARGUMENTS;
                CallFrame cf = frameOfEnv.get(f);
                name = "Arguments of " + cf.name;
            } else if (isTupleFrame(f)) { kind = Scope.Kind.TUPLE; name = "Tuple bindings"; }
            else { kind = Scope.Kind.LOCAL; name = localNo++ == 0 ? "Local" : "Local (outer " + (localNo - 1) + ")"; }
            if (!vars.isEmpty() || kind == Scope.Kind.GLOBAL) res.add(new Scope(kind, name, vars));
            if (f == base) pastBase = true;
        }
        return res;
    }

    private static boolean isTupleFrame(Frame f) {
        return f.getLocalBindings().containsKey("@");
    }

    /** value of a variable ({@code $name}, {@code $}, {@code $$}, {@code @}) as seen from a frame */
    public Object variable(String name, int frameId) {
        requireSuspended();
        Active a = activeOfFrameId(frameId);
        if (name.equals("$")) return DebugAccess.contextValue(a.input);
        if (name.equals("$$")) return a.env.lookup("$");
        if (name.startsWith("$")) name = name.substring(1);
        return a.env.lookup(name);
    }

    /**
     * evaluates a JSONata expression in the scope of a frame ({@code $} = the frame's context,
     * all visible variables, {@code $$} = root). Runs on the calling thread without debugger notifications.
     */
    public Object evaluate(String expr, int frameId) {
        requireSuspended();
        Active a = activeOfFrameId(frameId);
        return DebugAccess.evaluateInScope(DebugAccess.parse(expr), DebugAccess.contextValue(a.input), a.env);
    }

    // ------------------------------------------------------------------ DebugHook (worker thread)

    @Override
    public void enter(Symbol node, Object input, Frame env) {
        if (internal || Thread.currentThread() != worker) return;
        if (terminating) throw new TerminatedByDebugger();
        NodeInfo info = index.get(node);
        Active caller = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        if (frames.isEmpty()) frames.add(new CallFrame("<main>", null, null, 0, null, false));
        NodeInfo lambda = info != null ? index.lambdaOfBody(node) : null;
        if (lambda != null) pushCallFrame(lambda, caller, env);
        Active a = new Active(node, info, input, env);
        stack.add(a);
        if (info == null || !index.isExecutable(info)) return;   // foreign ($eval) or synthetic node

        int depth = stack.size();
        Breakpoint hit = null;
        List<Breakpoint> bps = bpByNode.get(info);
        if (bps != null) {
            for (Breakpoint bp : bps) {
                if (!bp.enabled) continue;
                Boolean c = checkCondition(bp, a);
                if (c == null) { hit = bp; break; }        // condition error: stop and report
                if (!c) continue;
                bp.hits++;
                if (!bp.hitMatches(bp.hits)) continue;
                if (bp.logMessage != null) { log(bp, a); continue; }
                hit = bp;
                break;
            }
        }
        if (hit != null) { suspend(StopReason.BREAKPOINT, Phase.ENTER, a, hit, null, null); return; }
        if (!stoppable(info)) return;
        if (entryPending) { entryPending = false; suspend(StopReason.ENTRY, Phase.ENTER, a, null, null, null); return; }
        if (pauseRequested) { suspend(StopReason.PAUSE, Phase.ENTER, a, null, null, null); return; }
        if (stepMode == StepMode.IN || (stepMode == StepMode.OVER && depth <= stepDepth))
            suspend(StopReason.STEP, Phase.ENTER, a, null, null, null);
    }

    @Override
    public void exit(Symbol node, Object input, Frame env, Object result) {
        if (internal || Thread.currentThread() != worker) return;
        if (terminating) throw new TerminatedByDebugger();
        int depth = stack.size();
        Active a = stack.get(depth - 1);
        if (a.info != null && depth >= 2) {
            NodeInfo thunk = index.tailCallOf(node);
            if (thunk != null) {
                // the trampoline in apply() evaluated the procedure of a tail call; the next lambda body
                // entered directly under the current call site is that tail call
                stack.get(depth - 2).pendingTailCallName = callName(node);
            }
        }
        if (a.info != null && index.isExecutable(a.info)) {
            boolean stop = (stepMode == StepMode.OUT && depth <= stepDepth)
                || ((stepMode == StepMode.IN || stepMode == StepMode.OVER) && depth < stepDepth)
                || (stepMode != StepMode.NONE && depth == 1);   // end of the evaluation: show the result
            if (stop) suspend(StopReason.STEP, Phase.EXIT, a, null, result, null);
        }
        pop();
    }

    @Override
    public void error(Symbol node, Object input, Frame env, Throwable error) {
        if (internal || Thread.currentThread() != worker) return;
        if (terminating || error instanceof TerminatedByDebugger) { pop(); return; }
        Active a = stack.get(stack.size() - 1);
        if (stopOnError && error != lastReportedError && a.info != null) {
            lastReportedError = error;
            suspend(StopReason.EXCEPTION, Phase.ERROR, a, null, null, error);
        }
        pop();
    }

    private void pop() {
        stack.remove(stack.size() - 1);
        if (frames.size() > 1 && frames.get(frames.size() - 1).base >= stack.size())
            frames.remove(frames.size() - 1);
    }

    private static boolean stoppable(NodeInfo n) {
        switch (n.kind) {
            case "string": case "number": case "value": case "regex": return false;
            default: return true;
        }
    }

    private void pushCallFrame(NodeInfo lambda, Active caller, Frame env) {
        String name;
        boolean tail = false;
        NodeInfo callSite = caller != null ? caller.info : null;
        if (caller != null && caller.pendingTailCallName != null) {
            name = caller.pendingTailCallName;
            caller.pendingTailCallName = null;
            tail = true;
        } else if (caller != null && caller.info != null) {
            name = calleeName(caller, lambda);
        } else {
            name = "λ";
        }
        name = name + " [λ@" + lambda.startLine + ":" + lambda.startColumn + "]";
        frames.add(new CallFrame(name, lambda, callSite, stack.size(), env, tail));
    }

    /** name of the function invoked by a call site, e.g. "$f", or "λ via $map" for a callback */
    private String calleeName(Active caller, NodeInfo lambda) {
        Symbol proc = null;
        switch (caller.info.kind) {
            case "function": case "partial": proc = DebugAccess.procedureOf(caller.node); break;
            case "apply": {
                for (NodeInfo c : caller.info.children) if ("rhs".equals(c.role)) proc = c.symbol;
                if (proc != null && ("function".equals(proc.getType()) || "partial".equals(proc.getType())))
                    proc = DebugAccess.procedureOf(proc);
                break;
            }
            default:
        }
        if (proc == null) return "λ";
        if ("variable".equals(proc.getType())) {
            Object v = caller.env.lookup(String.valueOf(proc.getValue()));
            if (DebugAccess.isLambdaValue(v)) return "$" + proc.getValue();   // user lambda (or partial application)
            return "λ via $" + proc.getValue();                             // callback of a native function
        }
        return "λ";
    }

    private static String callName(Symbol procedure) {
        if ("variable".equals(procedure.getType())) return "$" + procedure.getValue();
        return "λ";
    }

    /** true / false, or null if the condition failed */
    private Boolean checkCondition(Breakpoint bp, Active a) {
        if (bp.conditionExpr == null) return true;
        internal = true;
        try {
            Object v = DebugAccess.evaluateInScope(bp.conditionExpr, DebugAccess.contextValue(a.input), a.env);
            return DebugAccess.truthy(v);
        } catch (RuntimeException e) {
            events.add(new DebugEvent.Output("breakpoint #" + bp.id + ": condition failed: " + e.getMessage(), bp.node));
            return null;
        } finally {
            internal = false;
        }
    }

    private static final Pattern INTERPOLATION = Pattern.compile("\\{([^}]*)\\}");

    private void log(Breakpoint bp, Active a) {
        internal = true;
        try {
            Matcher m = INTERPOLATION.matcher(bp.logMessage);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String v;
                try {
                    v = DebugAccess.render(DebugAccess.evaluateInScope(DebugAccess.parse(m.group(1)),
                        DebugAccess.contextValue(a.input), a.env));
                } catch (RuntimeException e) {
                    v = "<error: " + e.getMessage() + ">";
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(v));
            }
            m.appendTail(sb);
            events.add(new DebugEvent.Output(sb.toString(), a.info));
        } finally {
            internal = false;
        }
    }

    /** blocks the worker until the controller sends a command */
    private void suspend(StopReason reason, Phase phase, Active a, Breakpoint bp, Object value, Throwable err) {
        pauseRequested = false;
        stepMode = StepMode.NONE;
        int depth = stack.size();
        Stopped ev = new Stopped(reason, phase, a.info, bp, value, err, depth, frames.size());
        long t0 = System.currentTimeMillis();
        Command c;
        synchronized (lock) {
            suspended = ev;
            command = null;
            events.add(ev);
            try {
                while (command == null) lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                command = Command.TERMINATE;
            }
            c = command;
            command = null;
            suspended = null;
        }
        // do not count the time spent suspended against the evaluation timeout guardrail
        Frame base = a.env.getBase();
        if (base != null) base.time += System.currentTimeMillis() - t0;
        switch (c) {
            case RESUME: stepMode = StepMode.NONE; break;
            case STEP_IN: stepMode = StepMode.IN; stepDepth = depth; break;
            case STEP_OVER: stepMode = StepMode.OVER; stepDepth = depth; break;
            case STEP_OUT:
                stepMode = StepMode.OUT;
                // inside a lambda: until its body returns; in the main frame: until the parent returns
                stepDepth = frames.size() > 1 ? frames.get(frames.size() - 1).base + 1 : depth - 1;
                break;
            case TERMINATE:
                terminating = true;
                throw new TerminatedByDebugger();
        }
    }
}
