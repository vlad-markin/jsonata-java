package compat.migration;

/** Helpers to map jsonata-js AST end positions back to token spans in the expression text. */
public final class Source {
    private Source() {}

    /**
     * Start index of a string literal token whose AST value is {@code value} and that ends at
     * {@code end}, or -1 if the raw token cannot be identified unambiguously (escapes etc.).
     */
    public static int stringStart(String src, String value, int end) {
        if (src == null || value == null || end > src.length()) return -1;
        for (char c : value.toCharArray())
            if (c == '\\' || c == '"' || c == '\'' || c < 0x20) return -1;
        int start = end - value.length() - 2;
        if (start < 0) return -1;
        char q = src.charAt(start);
        if ((q == '"' || q == '\'') && src.charAt(end - 1) == q && src.startsWith(value, start + 1))
            return start;
        return -1;
    }

    /** Start index of the regex literal ending (after its flags) at {@code end}, or -1. */
    public static int regexStart(String src, int end) {
        if (src == null || end > src.length()) return -1;
        for (int s = 0; s < end - 1; s++) {
            if (src.charAt(s) != '/') continue;
            int close = scanRegex(src, s + 1);
            if (close < 0) continue;
            int f = close + 1;
            while (f < src.length() && (src.charAt(f) == 'i' || src.charAt(f) == 'm')) f++;
            if (f == end) return s;
        }
        return -1;
    }

    /** index of the closing slash (jsonata tokenizer rules: escapes, depth of ([{), or -1 */
    static int scanRegex(String src, int from) {
        int depth = 0;
        for (int p = from; p < src.length(); p++) {
            char c = src.charAt(p);
            if (c == '/' && depth == 0) {
                int bs = 0;
                while (p - bs - 1 >= from && src.charAt(p - bs - 1) == '\\') bs++;
                if (bs % 2 == 0) return p == from ? -1 : p;
            }
            if ((c == '(' || c == '[' || c == '{') && src.charAt(p - 1) != '\\') depth++;
            if ((c == ')' || c == ']' || c == '}') && src.charAt(p - 1) != '\\') depth--;
        }
        return -1;
    }

    /** JavaScript canonical array index ("0".."4294967294") - such keys are ordered first by JS objects */
    public static boolean isArrayIndexKey(String k) {
        if (k == null || k.isEmpty() || k.length() > 10) return false;
        if (k.equals("0")) return true;
        if (k.charAt(0) < '1' || k.charAt(0) > '9') return false;
        for (int i = 1; i < k.length(); i++) if (!Character.isDigit(k.charAt(i))) return false;
        return Long.parseLong(k) < 4294967295L;
    }
}
