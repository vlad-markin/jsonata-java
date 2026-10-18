package com.dashjoin.jsonata.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.dashjoin.jsonata.DebugAccess;
import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;
import com.dashjoin.jsonata.debug.DebugEvent.Phase;
import com.dashjoin.jsonata.debug.DebugEvent.StopReason;
import com.dashjoin.jsonata.debug.DebugEvent.Stopped;
import com.dashjoin.jsonata.json.Json;

public class DebuggerTest {

    static final long T = 10_000;

    static final String ORDERS = "{\"Account\": {\"Name\": \"Firefly\", \"Order\": ["
        + "{\"OrderID\": \"o1\", \"Product\": [{\"Name\": \"Hat\", \"Price\": 10, \"Quantity\": 2},"
        + "                              {\"Name\": \"Cap\", \"Price\": 5, \"Quantity\": 1}]},"
        + "{\"OrderID\": \"o2\", \"Product\": [{\"Name\": \"Bag\", \"Price\": 20, \"Quantity\": 3}]}]}}";

    static Object json(String s) { return Json.parseJson(s); }

    static String text(AstIndex idx, NodeInfo n) {
        return idx.source().text().substring(n.start, n.end);
    }

    static Stopped nextStop(DebugSession s) throws InterruptedException {
        DebugEvent e = s.nextStopOrEnd(T, null);
        if (e instanceof Stopped) return (Stopped) e;
        fail("expected a stop, got " + e);
        return null;
    }

    static DebugEvent.Terminated end(DebugSession s) throws InterruptedException {
        DebugEvent e = s.nextStopOrEnd(T, null);
        if (e instanceof DebugEvent.Terminated) return (DebugEvent.Terminated) e;
        fail("expected termination, got " + e);
        return null;
    }

    // ------------------------------------------------------------------ ranges

    @Test
    public void rangesOfPath() {
        String src = "Account.Order[0].Product.(Price * Quantity)";
        AstIndex idx = new AstIndex(Jsonata.jsonata(src), src);
        NodeInfo root = idx.root();
        assertEquals("path", root.kind);
        assertEquals(0, root.start);
        assertEquals(src.length(), root.end);
        List<String> steps = root.children().stream().map(n -> text(idx, n)).collect(Collectors.toList());
        assertEquals(List.of("Account", "Order[0]", "Product", "(Price * Quantity)"), steps);
        NodeInfo filter = root.children().get(1).children().get(0);
        assertEquals("filter", filter.kind);
        assertEquals("[0]", text(idx, filter));
        NodeInfo mul = root.children().get(3).children().get(0);
        assertEquals("binary", mul.kind);
        assertEquals("Price * Quantity", text(idx, mul));
    }

    @Test
    public void rangesMultiLineAndLambda() {
        String src = "(\n  $f := function($x) {\n    $x * 2\n  };\n  $map([1, 2], $f)\n)";
        AstIndex idx = new AstIndex(Jsonata.jsonata(src), src);
        NodeInfo lambda = idx.nodes().stream().filter(n -> n.kind.equals("lambda")).findFirst().get();
        assertEquals(2, lambda.startLine);
        assertEquals(9, lambda.startColumn);
        assertEquals(4, lambda.endLine);
        assertEquals(4, lambda.endColumn); // exclusive: just after '}'
        NodeInfo body = lambda.children().stream().filter(n -> "body".equals(n.role)).findFirst().get();
        assertEquals("$x * 2", text(idx, body));
        assertEquals("3:5-3:11", body.rangeString());
        NodeInfo call = idx.nodes().stream().filter(n -> n.kind.equals("function")).findFirst().get();
        assertEquals("$map([1, 2], $f)", text(idx, call));
        assertEquals("$map", call.value);
    }

    @Test
    public void rangesOfOperators() {
        String[][] cases = {
            {"a.b ? 'x' : 'y'", "condition", "a.b ? 'x' : 'y'"},
            {"-5 + 3", "binary", "-5 + 3"},
            {"x ~> $uppercase()", "apply", "x ~> $uppercase()"},
            {"$x := 1", "bind", "$x := 1"},
            {"[1..3]", "unary", "[1..3]"},
            {"{'a': 1}", "unary", "{'a': 1}"},
            {"| $ | {'a': 1} |", "transform", "| $ | {'a': 1} |"},
            {"/* comment */ foo", "path", "foo"},
            {"$string(1)", "function", "$string(1)"},
            {"$substring(?, 1)", "partial", "$substring(?, 1)"},
            {"`a b`.c", "path", "`a b`.c"},
            {"x[/ab/i]", "path", "x[/ab/i]"},
        };
        for (String[] c : cases) {
            AstIndex idx = new AstIndex(Jsonata.jsonata(c[0]), c[0]);
            assertEquals(c[1], idx.root().kind, c[0]);
            assertEquals(c[2], text(idx, idx.root()), c[0]);
        }
        String src = "x[/ab/i]";
        AstIndex idx = new AstIndex(Jsonata.jsonata(src), src);
        NodeInfo regex = idx.nodes().stream().filter(n -> n.kind.equals("regex")).findFirst().get();
        assertEquals("/ab/i", text(idx, regex));
    }

    /** every evaluated node of every expression in the JSONata test suite has a sane range */
    @Test
    public void rangesForWholeTestSuite() throws Exception {
        File groups = new File("jsonata/test/test-suite/groups");
        if (!groups.isDirectory()) return;
        int parsed = 0;
        for (File g : groups.listFiles()) {
            File[] files = g.listFiles();
            if (files == null) continue;
            for (File f : files) {
                Object content;
                try { content = Json.parseJson(Files.readString(f.toPath())); } catch (RuntimeException e) { continue; }
                List<Object> cases = content instanceof List ? (List<Object>) content : List.of(content);
                for (Object c : cases) {
                    Object expr = ((Map<?, ?>) c).get("expr");
                    if (!(expr instanceof String)) continue;
                    String src = (String) expr;
                    Jsonata j;
                    try { j = Jsonata.jsonata(src); } catch (RuntimeException e) { continue; }
                    if (j.getErrors() != null) continue;
                    parsed++;
                    AstIndex idx = new AstIndex(j, src);
                    for (NodeInfo n : idx.nodes()) {
                        if (!idx.isExecutable(n) && !n.hasRange()) continue;
                        if (n.kind.equals("variable") && "$exists".equals(n.value) && !n.hasRange()) continue; // synthetic of ??
                        assertTrue(n.hasRange(), f + ": " + src + " -> " + n);
                        assertTrue(n.start >= 0 && n.start <= n.end && n.end <= src.length(), f + ": " + src + " -> " + n);
                        if (n.parent != null && n.parent.hasRange() && !"group".equals(n.parent.kind))
                            assertTrue(n.start >= n.parent.start && n.end <= n.parent.end,
                                f + ": child outside parent: " + src + " -> " + n + " in " + n.parent);
                    }
                    assertNotNull(idx.nodeAt(idx.root().start), src);
                }
            }
        }
        assertTrue(parsed > 1000, "parsed " + parsed);
    }

    /**
     * positions used in error messages are unchanged (jsonata-js: offset after the token).
     * (Verified for all 226 error cases of the test suite against the unmodified engine;
     * a few are pinned here.)
     */
    @Test
    public void errorPositionsUnchanged() {
        Object[][] cases = {
            {"[1,2)", "S0202", 5},
            {"$replace(\"foo\", \"o, \"rr\")", "S0202", 24},
            {"unknown(function)", "T1006", 8},
            {"\"no end", "S0101", 7},
        };
        for (Object[] c : cases) {
            JException e = assertThrows(JException.class, () -> Jsonata.jsonata((String) c[0]).evaluate(null), (String) c[0]);
            assertEquals(c[1], e.getError(), (String) c[0]);
            assertEquals(c[2], e.getLocation(), (String) c[0]);
        }
    }

    // ------------------------------------------------------------------ node at position

    @Test
    public void nodeAtPosition() {
        String src = "Account.Order.Product[Price > 5].Name\n  ~> $join(', ')";
        AstIndex idx = new AstIndex(Jsonata.jsonata(src), src);
        assertEquals("Account", text(idx, idx.nodeAt(1, 1)));
        assertEquals("Order", text(idx, idx.nodeAt(1, 10)));
        assertEquals("Price", text(idx, idx.nodeAt(1, 23)));
        assertEquals("5", text(idx, idx.nodeAt(1, 31)));
        assertEquals("Price > 5", text(idx, idx.nodeAt(1, 29)));   // on the operator
        assertEquals("$join", text(idx, idx.nodeAt(2, 6)));
        assertEquals("', '", text(idx, idx.nodeAt(2, 12)));
        assertEquals("apply", idx.nodeAt(2, 3).kind);              // on '~>'
        assertEquals("Name", text(idx, idx.nodeAt(1, 38)));         // caret right after the last token of line 1
        NodeInfo line2 = idx.firstNodeOnLine(2);
        assertEquals("$join(', ')", text(idx, line2));
    }

    // ------------------------------------------------------------------ breakpoints

    @Test
    public void breakpointOnPathStepHitOncePerElement() throws Exception {
        String src = "Account.Order.Product.Price";
        DebugSession s = new DebugSession(src);
        Breakpoint bpProduct = s.setBreakpoint(1, 15);   // Product
        Breakpoint bpPrice = s.setBreakpoint(1, 23);     // Price
        assertEquals("Product", bpProduct.node.value);
        s.start(json(ORDERS));
        List<String> hits = new ArrayList<>();
        DebugEvent e;
        while ((e = s.nextStopOrEnd(T, null)) instanceof Stopped) {
            Stopped st = (Stopped) e;
            assertEquals(StopReason.BREAKPOINT, st.reason);
            Object ctx = s.variable("$", 0);
            hits.add(st.node.value + ":" + (st.node.value.equals("Product") ? ((Map<?, ?>) ctx).get("OrderID") : ((Map<?, ?>) ctx).get("Name")));
            s.resume();
        }
        // a path is evaluated step by step over the whole sequence (not depth-first per element)
        assertEquals(List.of("Product:o1", "Product:o2", "Price:Hat", "Price:Cap", "Price:Bag"), hits);
        assertEquals(List.of(10, 5, 20), ((DebugEvent.Terminated) e).result);
        assertEquals(2, bpProduct.hits());
        assertEquals(3, bpPrice.hits());
    }

    @Test
    public void conditionalAndHitCountBreakpoints() throws Exception {
        String src = "Account.Order.Product.(Price * Quantity)";
        DebugSession s = new DebugSession(src);
        s.setBreakpoint(1, 30, "Price > 5", null, null);       // on '*'
        s.start(json(ORDERS));
        Stopped st = nextStop(s);
        assertEquals("binary", st.node.kind);
        assertEquals("Hat", ((Map<?, ?>) s.variable("$", 0)).get("Name"));
        s.resume();
        nextStop(s);
        assertEquals("Bag", ((Map<?, ?>) s.variable("$", 0)).get("Name"));
        s.resume();
        end(s);

        DebugSession s2 = new DebugSession(src);
        Breakpoint bp = s2.setBreakpoint(1, 30, null, "==2", null);
        s2.start(json(ORDERS));
        nextStop(s2);
        assertEquals("Cap", ((Map<?, ?>) s2.variable("$", 0)).get("Name"));
        s2.resume();
        end(s2);
        assertEquals(3, bp.hits());

        DebugSession s3 = new DebugSession(src);
        s3.setBreakpoint(1, 30, null, null, "{Name} costs {Price * Quantity}");
        s3.start(json(ORDERS));
        List<DebugEvent.Output> out = new ArrayList<>();
        assertTrue(s3.nextStopOrEnd(T, out) instanceof DebugEvent.Terminated);
        assertEquals(List.of("Hat costs 20", "Cap costs 5", "Bag costs 60"),
            out.stream().map(o -> o.text).collect(Collectors.toList()));
    }

    // ------------------------------------------------------------------ variables / call stack

    @Test
    public void variablesInLambdaCalledByMap() throws Exception {
        String src = "(\n  $factor := 10;\n  $f := function($x) {\n    $x * $factor\n  };\n  $map(Account.Order.Product.Price, $f)\n)";
        DebugSession s = new DebugSession(src);
        s.setBreakpoint(4, 8);   // '*'
        s.start(json(ORDERS));
        Stopped st = nextStop(s);
        assertEquals("binary", st.node.kind);
        List<StackFrameInfo> bt = s.stackTrace();
        assertEquals(2, bt.size());
        assertTrue(bt.get(0).name.startsWith("λ via $map"), bt.get(0).name);
        assertEquals("$map", bt.get(0).callSite.value);
        assertEquals("<main>", bt.get(1).name);
        assertEquals("function", bt.get(1).node.kind);  // main frame is inside the $map call

        List<Scope> scopes = s.scopes(0);
        assertEquals(Scope.Kind.CONTEXT, scopes.get(0).kind);
        Scope args = scopes.stream().filter(sc -> sc.kind == Scope.Kind.ARGUMENTS).findFirst().get();
        assertEquals(10, args.get("$x").value);
        Scope local = scopes.stream().filter(sc -> sc.kind == Scope.Kind.LOCAL).findFirst().get();
        assertEquals(10, local.get("$factor").value);
        assertTrue(DebugAccess.isLambdaValue(local.get("$f").value));
        assertEquals("Firefly", ((Map<?, ?>) ((Map<?, ?>) scopes.get(0).get("$$").value).get("Account")).get("Name"));
        assertEquals(100, s.evaluate("$x * $factor", 0));
        assertEquals("Firefly", s.evaluate("$$.Account.Name", 0));
        s.resume();
        nextStop(s);
        assertEquals(5, s.variable("$x", 0));
        s.clearBreakpoints();
        s.resume();
        assertEquals(List.of(100, 50, 200), end(s).result);
    }

    @Test
    public void focusAndIndexBindings() throws Exception {
        String src = "Account.Order#$i.Product@$p.{'o': $i, 'n': $p.Name}";
        DebugSession s = new DebugSession(src);
        NodeInfo obj = s.index().nodes().stream().filter(n -> n.kind.equals("unary")).findFirst().get();
        s.setBreakpoint(obj, null, null, null);
        s.start(json(ORDERS));
        nextStop(s);
        List<Scope> scopes = s.scopes(0);
        Scope tuple = scopes.stream().filter(sc -> sc.kind == Scope.Kind.TUPLE).findFirst().get();
        assertEquals(0, tuple.get("$i").value);
        assertEquals("Hat", ((Map<?, ?>) tuple.get("$p").value).get("Name"));
        Variable focus = scopes.get(0).get("@");
        assertNotNull(focus);
        s.resume();
        nextStop(s);
        s.resume();
        nextStop(s);
        assertEquals(1, s.variable("$i", 0));
        s.resume();
        end(s);
    }

    @Test
    public void recursionAndTailCalls() throws Exception {
        // non tail-recursive: the stack grows
        String fac = "(\n  $fac := function($n) { $n <= 1 ? 1 : $n * $fac($n - 1) };\n  $fac(3)\n)";
        DebugSession s = new DebugSession(fac);
        NodeInfo cond = s.index().nodes().stream().filter(n -> n.kind.equals("binary") && "<=".equals(n.value)).findFirst().get();
        s.setBreakpoint(cond, null, "3", null);
        s.start(null);
        nextStop(s);
        List<StackFrameInfo> bt = s.stackTrace();
        assertEquals(4, bt.size());
        assertTrue(bt.get(0).name.startsWith("$fac"), bt.get(0).name);
        assertEquals(1, s.variable("$n", 0));
        assertEquals(2, s.variable("$n", 1));
        assertEquals(3, s.variable("$n", 2));
        s.resume();
        assertEquals(6, end(s).result);

        // tail-recursive: the trampoline replaces the frame
        String loop = "(\n  $loop := function($n, $acc) { $n = 0 ? $acc : $loop($n - 1, $acc + $n) };\n  $loop(3, 0)\n)";
        DebugSession t = new DebugSession(loop);
        NodeInfo eq = t.index().nodes().stream().filter(n -> n.kind.equals("binary") && "=".equals(n.value)).findFirst().get();
        t.setBreakpoint(eq, null, null, null);
        t.start(null);
        List<Integer> depths = new ArrayList<>();
        List<Boolean> tail = new ArrayList<>();
        DebugEvent e;
        while ((e = t.nextStopOrEnd(T, null)) instanceof Stopped) {
            List<StackFrameInfo> st = t.stackTrace();
            depths.add(st.size());
            tail.add(st.get(0).tailCall);
            assertTrue(st.get(0).name.startsWith("$loop"), st.get(0).name);
            t.resume();
        }
        assertEquals(List.of(2, 2, 2, 2), depths);
        assertEquals(List.of(false, true, true, true), tail);
        assertEquals(6, ((DebugEvent.Terminated) e).result);

        // a breakpoint on the tail call itself is moved to the thunk (the call is never evaluated as a node)
        DebugSession u = new DebugSession(loop);
        NodeInfo tailCall = u.index().nodeAt(loop.indexOf("$loop($n") + 5);   // '(' of the tail call
        assertEquals("function", tailCall.kind);
        assertFalse(u.index().isExecutable(tailCall));
        Breakpoint bp = u.setBreakpoint(tailCall, null, null, null);
        assertEquals("lambda", bp.node.kind);
        u.start(null);
        int n = 0;
        while (u.nextStopOrEnd(T, null) instanceof Stopped) { n++; u.resume(); }
        assertEquals(3, n);
    }

    // ------------------------------------------------------------------ stepping

    @Test
    public void steppingOverPathOverArray() throws Exception {
        String src = "Account.Order.OrderID";
        DebugSession s = new DebugSession(src);
        s.setStopOnEntry(true);
        s.start(json(ORDERS));
        List<String> trace = new ArrayList<>();
        DebugEvent e = s.nextStopOrEnd(T, null);
        while (e instanceof Stopped) {
            Stopped st = (Stopped) e;
            trace.add(st.phase + " " + text(s.index(), st.node) + (st.phase == Phase.EXIT ? " = " + DebugAccess.render(st.result) : ""));
            s.stepOver();
            e = s.nextStopOrEnd(T, null);
        }
        // step over at the path enters its first step; at a step it moves to the same step for the next element
        assertEquals(List.of(
            "ENTER Account.Order.OrderID",
            "EXIT Account.Order.OrderID = [\"o1\",\"o2\"]"), trace);

        DebugSession s2 = new DebugSession(src);
        s2.setStopOnEntry(true);
        s2.start(json(ORDERS));
        trace.clear();
        e = s2.nextStopOrEnd(T, null);
        boolean first = true;
        while (e instanceof Stopped) {
            Stopped st = (Stopped) e;
            trace.add(st.phase + " " + text(s2.index(), st.node));
            if (first) { s2.stepIn(); first = false; } else s2.stepOver();
            e = s2.nextStopOrEnd(T, null);
        }
        assertEquals(List.of(
            "ENTER Account.Order.OrderID",
            "ENTER Account",
            "ENTER Order",
            "ENTER OrderID",      // for order o1
            "ENTER OrderID",      // for order o2
            "EXIT Account.Order.OrderID"), trace);
    }

    @Test
    public void stepInAndOutOfLambda() throws Exception {
        String src = "(\n  $f := function($x) { $x + 1 };\n  $f(1) + $f(2)\n)";
        DebugSession s = new DebugSession(src);
        s.setBreakpoint(3, 5);      // '(' of $f(1)
        s.start(null);
        Stopped st = nextStop(s);
        assertEquals("$f(1)", text(s.index(), st.node));
        s.stepIn();
        st = nextStop(s);
        assertEquals("$f", text(s.index(), st.node));           // procedure
        s.stepIn();
        st = nextStop(s);
        assertEquals("$x + 1", text(s.index(), st.node));      // lambda body
        assertEquals(2, s.stackTrace().size());
        s.stepIn();
        st = nextStop(s);
        assertEquals("$x", text(s.index(), st.node));
        s.stepOut();
        st = nextStop(s);
        assertEquals(Phase.EXIT, st.phase);                    // body returned
        assertEquals("$x + 1", text(s.index(), st.node));
        assertEquals(2, st.result);
        s.stepOver();
        st = nextStop(s);
        assertEquals(Phase.EXIT, st.phase);                    // the call returned
        assertEquals("$f(1)", text(s.index(), st.node));
        assertEquals(1, s.stackTrace().size());
        s.stepOver();
        st = nextStop(s);
        assertEquals(Phase.ENTER, st.phase);
        assertEquals("$f(2)", text(s.index(), st.node));
        s.stepOver();                                           // over the whole call
        st = nextStop(s);
        assertEquals(Phase.EXIT, st.phase);
        assertEquals("$f(1) + $f(2)", text(s.index(), st.node));
        assertEquals(5, st.result);
        s.resume();
        assertEquals(5, end(s).result);
    }

    @Test
    public void pauseRunningEvaluation() throws Exception {
        String src = "$count([1..2000000].($ * 2))";
        DebugSession s = new DebugSession(src);
        s.start(null);
        Thread.sleep(50);
        s.pause();
        Stopped st = nextStop(s);
        assertEquals(StopReason.PAUSE, st.reason);
        assertTrue(st.depth >= 1);
        s.terminate();
        DebugEvent.Terminated t = end(s);
        assertTrue(t.error instanceof DebugSession.TerminatedByDebugger, String.valueOf(t.error));
    }

    // ------------------------------------------------------------------ errors

    @Test
    public void stopOnError() throws Exception {
        String src = "Account.Order.Product.(\n  Price & Quantity + Name\n)";
        DebugSession s = new DebugSession(src);
        s.start(json(ORDERS));
        Stopped st = nextStop(s);
        assertEquals(StopReason.EXCEPTION, st.reason);
        assertEquals(Phase.ERROR, st.phase);
        assertEquals("Price & Quantity + Name", text(s.index(), st.node));   // (Price & Quantity) + Name
        assertEquals("T2001", ((JException) st.error).getError());
        assertEquals("2:3-2:26", st.node.rangeString());
        assertEquals("Hat", s.evaluate("Name", 0));
        s.resume();
        DebugEvent.Terminated t = end(s);
        assertTrue(t.error instanceof JException);
        assertEquals("T2001", ((JException) t.error).getError());
    }

    // ------------------------------------------------------------------ engine integration

    @Test
    public void noDebuggerAndCallbacksStillWork() {
        Jsonata j = Jsonata.jsonata("Account.Order.Product.Price");
        Frame f = j.createFrame();
        List<String> entered = new ArrayList<>();
        f.setEvaluateEntryCallback((expr, input, env) -> entered.add(expr.getType()));
        assertEquals(List.of(10, 5, 20), j.evaluate(json(ORDERS), f));
        int withoutDebugger = entered.size();
        assertTrue(withoutDebugger > 0);

        // entry callback and debug hook together
        entered.clear();
        List<String> hooked = new ArrayList<>();
        f.setDebugHook(new DebugHook() {
            public void enter(com.dashjoin.jsonata.Parser.Symbol n, Object i, Frame e) { hooked.add(n.getType()); }
            public void exit(com.dashjoin.jsonata.Parser.Symbol n, Object i, Frame e, Object r) {}
            public void error(com.dashjoin.jsonata.Parser.Symbol n, Object i, Frame e, Throwable t) {}
        });
        assertEquals(List.of(10, 5, 20), j.evaluate(json(ORDERS), f));
        assertEquals(withoutDebugger, entered.size());
        assertEquals(entered, hooked);

        // the hook is not sticky: an evaluation without it does not call it
        hooked.clear();
        assertEquals(List.of(10, 5, 20), j.evaluate(json(ORDERS)));
        assertTrue(hooked.isEmpty());
    }

    @Test
    public void evalSourceIsNotSteppable() throws Exception {
        String src = "$eval('1 + a', {'a': 2}) + 1";
        DebugSession s = new DebugSession(src);
        s.setStopOnEntry(true);
        s.start(null);
        int stops = 0;
        DebugEvent e;
        while ((e = s.nextStopOrEnd(T, null)) instanceof Stopped) {
            Stopped st = (Stopped) e;
            assertNotNull(st.node);
            assertNull(s.index().get(null));
            stops++;
            s.stepIn();
        }
        assertTrue(stops > 2);
        assertEquals(4, ((DebugEvent.Terminated) e).result);
        assertFalse(s.isSuspended());
    }
}
