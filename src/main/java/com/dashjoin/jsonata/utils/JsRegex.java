/**
 * jsonata-java is the JSONata Java reference port
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dashjoin.jsonata.utils;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Translates a JavaScript regular expression (ECMAScript, without the 'u' flag, as created by
 * jsonata-js for /.../ literals) into an equivalent java.util.regex pattern.
 *
 * <ul>
 * <li>identity escapes (\A, \Z, \h, \R, \p ...) are literal characters as in JS</li>
 * <li>\s / \S use the ECMAScript white space set (includes NBSP, U+2028, BOM ...)</li>
 * <li>$ without m flag matches only at the end of input (Java also before a final line terminator)</li>
 * <li>. and ^/$ with m flag use the ECMAScript line terminators</li>
 * <li>[^] (any char), [] (nothing), [\b] (backspace), literal [ and &amp;&amp; in classes</li>
 * <li>\0, \cX, \xHH, &#92;uHHHH, octal escapes for back references to non-existing groups,
 *     literal { that does not start a quantifier</li>
 * <li>case-insensitive matching is Unicode-aware like JS (UNICODE_CASE)</li>
 * <li>Java-only syntax (possessive quantifiers, inline flags (?i), atomic groups) is rejected
 *     like a JavaScript SyntaxError</li>
 * </ul>
 * Not emulated: JS treats astral characters (e.g. emoji) as two UTF-16 code units, so "." matches
 * half of a surrogate pair in JS but the whole character in Java.
 */
public final class JsRegex {
    private JsRegex() {}

    static final String WS = "\\t\\n\\u000B\\f\\r \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff";
    static final String LT = "\\n\\r\\u2028\\u2029";

    private static final java.util.Map<Pattern, String> SOURCES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static Pattern compile(String source, boolean ignoreCase, boolean multiline) {
        int flags = 0;
        if (ignoreCase)
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern p = Pattern.compile(translate(source, multiline), flags);
        SOURCES.put(p, source);
        return p;
    }

    /**
     * Returns the JavaScript source of a regex compiled by {@link #compile} (Pattern.toString()
     * returns the translated Java pattern).
     */
    public static String source(Pattern p) {
        String s = SOURCES.get(p);
        return s != null ? s : p.pattern();
    }

    public static String translate(String src, boolean multiline) {
        int groups = countGroups(src);
        StringBuilder b = new StringBuilder(src.length() + 16);
        int n = src.length();
        int i = 0;
        boolean lastAtom = false; // can the next token be a quantifier target
        while (i < n) {
            char c = src.charAt(i);
            switch (c) {
                case '\\': {
                    i = escape(src, i, b, false, groups);
                    lastAtom = true;
                    continue;
                }
                case '[': {
                    i = charClass(src, i, b);
                    lastAtom = true;
                    continue;
                }
                case '.':
                    b.append("[^").append(LT).append(']');
                    lastAtom = true;
                    break;
                case '$':
                    b.append(multiline ? "(?=[" + LT + "]|\\z)" : "\\z");
                    lastAtom = false;
                    break;
                case '^':
                    b.append(multiline ? "(?:(?<=[" + LT + "])|\\A)" : "\\A");
                    lastAtom = false;
                    break;
                case '(': {
                    if (src.startsWith("(?", i)) {
                        if (!(src.startsWith("(?:", i) || src.startsWith("(?=", i) || src.startsWith("(?!", i)
                                || src.startsWith("(?<=", i) || src.startsWith("(?<!", i)
                                || (src.startsWith("(?<", i) && i + 3 < n && Character.isLetter(src.charAt(i + 3)))))
                            throw new PatternSyntaxException("Invalid group", src, i);
                    }
                    b.append(c);
                    lastAtom = false;
                    break;
                }
                case '*': case '+': case '?': {
                    b.append(c);
                    // lazy marker is allowed, possessive (Java only) is a syntax error in JS
                    if (i + 1 < n && src.charAt(i + 1) == '?') {
                        b.append('?');
                        i++;
                    }
                    if (i + 1 < n && (src.charAt(i + 1) == '+' || (src.charAt(i + 1) == '*'))
                            || (c != '?' && i + 1 < n && src.charAt(i + 1) == '?'))
                        throw new PatternSyntaxException("Nothing to repeat", src, i + 1);
                    lastAtom = false;
                    break;
                }
                case '{': {
                    int end = quantifierEnd(src, i);
                    if (end > 0 && lastAtom) {
                        b.append(src, i, end + 1);
                        i = end;
                        if (i + 1 < n && src.charAt(i + 1) == '?') {
                            b.append('?');
                            i++;
                        }
                        if (i + 1 < n && src.charAt(i + 1) == '+')
                            throw new PatternSyntaxException("Nothing to repeat", src, i + 1);
                        lastAtom = false;
                    } else {
                        if (end > 0)
                            throw new PatternSyntaxException("Nothing to repeat", src, i);
                        b.append("\\{");
                        lastAtom = true;
                    }
                    break;
                }
                case '}':
                    b.append("\\}");
                    lastAtom = true;
                    break;
                case ')': case '|':
                    b.append(c);
                    lastAtom = c == ')';
                    break;
                default:
                    if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(src.charAt(i + 1))) {
                        b.append(c).append(src.charAt(++i));
                    } else
                        b.append(c);
                    lastAtom = true;
            }
            i++;
        }
        return b.toString();
    }

    /** returns index of closing '}' if src[i..] is a JS quantifier {n}, {n,}, {n,m}, else -1 */
    static int quantifierEnd(String src, int i) {
        int j = i + 1, n = src.length();
        int d = j;
        while (j < n && Character.isDigit(src.charAt(j))) j++;
        if (j == d) return -1;
        if (j < n && src.charAt(j) == '}') return j;
        if (j < n && src.charAt(j) == ',') {
            j++;
            while (j < n && Character.isDigit(src.charAt(j))) j++;
            if (j < n && src.charAt(j) == '}') return j;
        }
        return -1;
    }

    static int countGroups(String src) {
        int count = 0;
        boolean inClass = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '\\') { i++; continue; }
            if (inClass) { if (c == ']') inClass = false; continue; }
            if (c == '[') { inClass = true; continue; }
            if (c == '(') {
                if (!src.startsWith("(?", i) || (src.startsWith("(?<", i) && !src.startsWith("(?<=", i) && !src.startsWith("(?<!", i)))
                    count++;
            }
        }
        return count;
    }

    static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0 && c < 128;
    }

    static void literal(int cp, StringBuilder b) {
        b.append(String.format("\\x{%x}", cp));
    }

    /** translates the escape at src[i] ('\\'), returns the index after it */
    static int escape(String src, int i, StringBuilder b, boolean inClass, int groups) {
        int n = src.length();
        if (i + 1 >= n) {
            // trailing backslash: JS SyntaxError "\ at end of pattern"
            throw new PatternSyntaxException("\\ at end of pattern", src, i);
        }
        char e = src.charAt(i + 1);
        switch (e) {
            case 'd': case 'D': case 'w': case 'W': case 't': case 'n': case 'r': case 'f':
                b.append('\\').append(e);
                return i + 2;
            case 'v':
                b.append("\\x0B");
                return i + 2;
            case 's':
                b.append(inClass ? WS : "[" + WS + "]");
                return i + 2;
            case 'S':
                if (inClass)
                    // negated set inside a class: Java supports nested classes
                    b.append("[^").append(WS).append(']');
                else
                    b.append("[^").append(WS).append(']');
                return i + 2;
            case 'b':
                b.append(inClass ? "\\x08" : "\\b");
                return i + 2;
            case 'B':
                if (inClass) { b.append('B'); return i + 2; }
                b.append("\\B");
                return i + 2;
            case 'c': {
                if (i + 2 < n && Character.isLetter(src.charAt(i + 2)) && src.charAt(i + 2) < 128) {
                    literal(src.charAt(i + 2) % 32, b);
                    return i + 3;
                }
                // \c not followed by a letter is a literal backslash and 'c'
                b.append("\\\\c");
                return i + 2;
            }
            case 'x':
                if (i + 3 < n && isHex(src.charAt(i + 2)) && isHex(src.charAt(i + 3))) {
                    literal(Integer.parseInt(src.substring(i + 2, i + 4), 16), b);
                    return i + 4;
                }
                b.append('x');
                return i + 2;
            case 'u':
                if (i + 6 <= n && isHex(src.charAt(i + 2)) && isHex(src.charAt(i + 3))
                        && isHex(src.charAt(i + 4)) && isHex(src.charAt(i + 5))) {
                    literal(Integer.parseInt(src.substring(i + 2, i + 6), 16), b);
                    return i + 6;
                }
                b.append('u');
                return i + 2;
            case 'k':
                if (groups > 0 && src.startsWith("<", i + 2) && !inClass) {
                    int end = src.indexOf('>', i + 3);
                    if (end > 0) {
                        b.append(src, i, end + 1);
                        return end + 1;
                    }
                }
                b.append('k');
                return i + 2;
            default:
                break;
        }
        if (e >= '0' && e <= '9') {
            int j = i + 1;
            while (j < n && Character.isDigit(src.charAt(j))) j++;
            String digits = src.substring(i + 1, j);
            if (!inClass && e != '0') {
                int ref = Integer.parseInt(digits.length() > 9 ? digits.substring(0, 9) : digits);
                if (ref <= groups) {
                    b.append('\\').append(digits);
                    return j;
                }
            }
            if (e == '0' && (j == i + 2)) {
                literal(0, b);
                return i + 2;
            }
            if (e >= '8') {
                // \8 \9 are identity escapes
                b.append(e);
                return i + 2;
            }
            // legacy octal escape (up to 3 octal digits, value <= 0377)
            int k = i + 1, v = 0, len = 0;
            while (k < n && len < 3 && src.charAt(k) >= '0' && src.charAt(k) <= '7' && v * 8 + (src.charAt(k) - '0') <= 0377) {
                v = v * 8 + (src.charAt(k) - '0');
                k++;
                len++;
            }
            literal(v, b);
            return k;
        }
        // identity escape: the character itself
        int cp = src.codePointAt(i + 1);
        literal(cp, b);
        return i + 1 + Character.charCount(cp);
    }

    /** translates a character class starting at src[i] ('['), returns the index after it */
    static int charClass(String src, int i, StringBuilder b) {
        int n = src.length();
        int j = i + 1;
        boolean negated = j < n && src.charAt(j) == '^';
        if (negated) j++;
        if (j < n && src.charAt(j) == ']') {
            // [] matches nothing, [^] matches any character
            b.append(negated ? "[\\s\\S]" : "(?!)");
            return j + 1;
        }
        b.append(negated ? "[^" : "[");
        while (j < n) {
            char c = src.charAt(j);
            if (c == ']') {
                b.append(']');
                return j + 1;
            }
            if (c == '\\') {
                j = escape(src, j, b, true, 0);
                continue;
            }
            if (c == '[' || c == '&' || c == '^') {
                b.append('\\').append(c);
            } else {
                b.append(c);
            }
            j++;
        }
        throw new PatternSyntaxException("Unterminated character class", src, i);
    }
}
