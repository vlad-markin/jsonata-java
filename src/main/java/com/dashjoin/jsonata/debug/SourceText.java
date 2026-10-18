package com.dashjoin.jsonata.debug;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps character offsets of an expression source to 1-based line / 1-based column and back.
 * Columns count UTF-16 code units (Java {@code char}s), lines are separated by {@code \n}
 * ({@code \r\n} is treated as one line break, the {@code \r} belongs to the line).
 */
public final class SourceText {

    private final String text;
    private final int[] lineStarts;

    public SourceText(String text) {
        this.text = text;
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\n') starts.add(i + 1);
        lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    public String text() { return text; }

    public int lineCount() { return lineStarts.length; }

    /** 1-based line of an offset */
    public int line(int offset) {
        int lo = 0, hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) lo = mid; else hi = mid - 1;
        }
        return lo + 1;
    }

    /** 1-based column of an offset */
    public int column(int offset) {
        return offset - lineStarts[line(offset) - 1] + 1;
    }

    /** offset of a 1-based line/column; clamps the column to the line length */
    public int offset(int line, int column) {
        if (line < 1 || line > lineStarts.length) throw new IllegalArgumentException("line out of range: " + line);
        int start = lineStarts[line - 1];
        int end = lineEnd(line);
        return Math.min(start + Math.max(column, 1) - 1, end);
    }

    public int lineStart(int line) { return lineStarts[line - 1]; }

    /** offset of the end of the line (excluding the line break) */
    public int lineEnd(int line) {
        int end = line < lineStarts.length ? lineStarts[line] - 1 : text.length();
        if (end > lineStarts[line - 1] && end <= text.length() && end > 0 && text.charAt(end - 1) == '\r') end--;
        return end;
    }

    public String lineText(int line) {
        return text.substring(lineStart(line), lineEnd(line));
    }

    /** "line:col" of an offset */
    public String pos(int offset) {
        return line(offset) + ":" + column(offset);
    }
}
