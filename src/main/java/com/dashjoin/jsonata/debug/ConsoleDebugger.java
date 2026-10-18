package com.dashjoin.jsonata.debug;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.dashjoin.jsonata.DebugAccess;
import com.dashjoin.jsonata.json.Json;

/**
 * Minimal console debugger for JSONata expressions (demonstration of {@link DebugSession}).
 *
 * <pre>
 * java -cp jsonata.jar com.dashjoin.jsonata.debug.ConsoleDebugger expr.jsonata [input.json]
 *
 * b L:C [if COND]   breakpoint at the innermost expression at line L, column C (1-based)
 * b L [if COND]     breakpoint at the first expression starting on line L
 * d N               delete breakpoint N            bl   list breakpoints
 * r                 run (start the evaluation)     c    continue
 * s                 step in       n   step over    o    step out
 * p EXPR            evaluate EXPR in the selected frame (e.g. p $x, p $$.Account, p Price * 2)
 * vars              show scopes / variables        bt   call stack
 * f N               select frame N                 l    list source
 * e on|off          stop on errors (default on)    ast  dump the AST with ranges
 * q                 quit (terminates the evaluation)
 * </pre>
 */
public final class ConsoleDebugger {

    private final DebugSession session;
    private final SourceText src;
    private final Object input;
    private final PrintStream out;
    private final boolean echo;
    private int frame;
    private boolean started;

    ConsoleDebugger(String source, Object input, PrintStream out, boolean echo) {
        this.session = new DebugSession(source);
        this.src = session.index().source();
        this.input = input;
        this.out = out;
        this.echo = echo;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: ConsoleDebugger <expression-file> [<input.json>]");
            System.exit(2);
        }
        String source = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        Object input = args.length > 1 ? Json.parseJson(Files.readString(Path.of(args[1]), StandardCharsets.UTF_8)) : null;
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        new ConsoleDebugger(source, input, out, System.console() == null)
            .run(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
    }

    void run(BufferedReader in) throws Exception {
        out.println("JSONata debugger: " + src.lineCount() + " line(s), " + session.index().nodes().size()
            + " AST nodes. Type 'r' to run, 'h' for help.");
        String line;
        while (true) {
            out.print("(jsonata) ");
            out.flush();
            line = in.readLine();
            if (line == null) { if (started && !session.isFinished()) session.terminate(); break; }
            line = line.trim();
            if (echo) out.println(line);
            if (line.isEmpty()) continue;
            try {
                if (!command(line)) break;
            } catch (IllegalArgumentException | IllegalStateException e) {
                out.println("error: " + e.getMessage());
            } catch (RuntimeException e) {
                out.println("error: " + e);
            }
        }
    }

    /** @return false to quit */
    private boolean command(String line) throws Exception {
        String cmd = line.split("\\s+", 2)[0];
        String arg = line.length() > cmd.length() ? line.substring(cmd.length()).trim() : "";
        switch (cmd) {
            case "h": case "help":
                out.println("b L[:C] [if COND] | d N | bl | r | c | s | n | o | p EXPR | vars | bt | f N | l | e on|off | ast | q");
                return true;
            case "b": breakpoint(arg); return true;
            case "d": out.println(session.removeBreakpoint(Integer.parseInt(arg)) ? "deleted" : "no such breakpoint"); return true;
            case "bl": for (Breakpoint bp : session.breakpoints()) out.println("  " + bp); return true;
            case "l": for (int i = 1; i <= src.lineCount(); i++) out.printf("%4d | %s%n", i, src.lineText(i)); return true;
            case "ast": out.print(session.index().dump()); return true;
            case "e": session.setStopOnError(!arg.equals("off")); out.println("stop on error: " + !arg.equals("off")); return true;
            case "q":
                if (started && session.isSuspended()) { session.terminate(); waitForStop(); }
                return false;
            case "r":
                if (started) { out.println("already running"); return true; }
                started = true;
                session.start(input);
                return waitForStop();
            case "c": session.resume(); return waitForStop();
            case "s": session.stepIn(); return waitForStop();
            case "n": session.stepOver(); return waitForStop();
            case "o": session.stepOut(); return waitForStop();
            case "p": out.println(DebugAccess.render(session.evaluate(arg, frame))); return true;
            case "vars": vars(); return true;
            case "bt": backtrace(); return true;
            case "f": {
                frame = Integer.parseInt(arg);
                StackFrameInfo f = session.stackTrace().get(frame);
                out.println(f);
                if (f.node != null) highlight(f.node);
                return true;
            }
            default:
                out.println("unknown command '" + cmd + "' (h for help)");
                return true;
        }
    }

    private void breakpoint(String arg) {
        String cond = null;
        int ifPos = arg.indexOf(" if ");
        if (ifPos >= 0) { cond = arg.substring(ifPos + 4).trim(); arg = arg.substring(0, ifPos).trim(); }
        Breakpoint bp;
        if (arg.contains(":")) {
            String[] lc = arg.split(":");
            bp = session.setBreakpoint(Integer.parseInt(lc[0].trim()), Integer.parseInt(lc[1].trim()), cond, null, null);
        } else {
            bp = session.setLineBreakpoint(Integer.parseInt(arg.trim()), cond, null, null);
        }
        out.println("breakpoint " + bp);
        highlight(bp.node);
    }

    /** waits for the next stop or the end; prints it. @return false if the evaluation ended */
    private boolean waitForStop() throws InterruptedException {
        List<DebugEvent.Output> output = new ArrayList<>();
        DebugEvent e = session.nextStopOrEnd(60_000, output);
        for (DebugEvent.Output o : output) out.println("log: " + o.text);
        frame = 0;
        if (e instanceof DebugEvent.Stopped) {
            DebugEvent.Stopped st = (DebugEvent.Stopped) e;
            StringBuilder sb = new StringBuilder("stopped: ").append(st.reason.toString().toLowerCase());
            if (st.breakpoint != null) sb.append(" #").append(st.breakpoint.id);
            sb.append(", ").append(st.phase == DebugEvent.Phase.ENTER ? "before" : st.phase == DebugEvent.Phase.EXIT ? "after" : "error in")
              .append(' ').append(st.node.label()).append(" at ").append(st.node.rangeString());
            List<StackFrameInfo> bt = session.stackTrace();
            if (bt.size() > 1) sb.append(" in ").append(bt.get(0).name);
            out.println(sb);
            highlight(st.node);
            if (st.phase == DebugEvent.Phase.EXIT) out.println("  => " + shorten(DebugAccess.render(st.result)));
            if (st.error instanceof com.dashjoin.jsonata.JException)
                out.println("  !! " + ((com.dashjoin.jsonata.JException) st.error).getError() + ": " + st.error.getMessage());
            else if (st.error != null) out.println("  !! " + st.error);
            out.println("  $ = " + shorten(DebugAccess.render(session.variable("$", 0))));
            return true;
        }
        if (e instanceof DebugEvent.Terminated) {
            DebugEvent.Terminated t = (DebugEvent.Terminated) e;
            if (t.error != null) out.println("terminated with error: " + t.error.getMessage());
            else out.println("result: " + DebugAccess.render(t.result));
            return false;
        }
        out.println("timeout waiting for the evaluation");
        return true;
    }

    private void vars() {
        for (Scope sc : session.scopes(frame)) {
            out.println(sc.name + ":");
            for (Variable v : sc.variables)
                out.println("  " + v.name + " = " + shorten(v.display()) + (v.shadowed ? "  (shadowed)" : ""));
        }
    }

    private void backtrace() {
        for (StackFrameInfo f : session.stackTrace()) {
            out.println((f.id == frame ? "* " : "  ") + "#" + f.id + " " + f.name + (f.tailCall ? " (tail call)" : "")
                + " at " + (f.node != null ? f.node.rangeString() + " " + f.node.label() : "?")
                + (f.callSite != null && f.lambda != null ? "  <- called from " + f.callSite.rangeString() : ""));
        }
    }

    /** prints the source lines of the node with the range underlined */
    void highlight(NodeInfo n) {
        if (n == null || !n.hasRange()) return;
        int last = Math.min(n.endLine, n.startLine + 2);
        for (int l = n.startLine; l <= last; l++) {
            String text = src.lineText(l);
            out.printf("%4d | %s%n", l, text);
            int from = l == n.startLine ? n.startColumn - 1 : leadingSpaces(text);
            int to = l == n.endLine ? n.endColumn - 1 : text.length();
            if (to <= from) to = from + 1;
            StringBuilder m = new StringBuilder("     | ");
            for (int i = 0; i < from; i++) m.append(text.length() > i && text.charAt(i) == '\t' ? '\t' : ' ');
            m.append('^');
            for (int i = from + 1; i < to; i++) m.append('~');
            out.println(m);
        }
        if (last < n.endLine) out.println("     | ... (until " + n.endLine + ":" + n.endColumn + ")");
    }

    private static int leadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static String shorten(String s) {
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }
}
