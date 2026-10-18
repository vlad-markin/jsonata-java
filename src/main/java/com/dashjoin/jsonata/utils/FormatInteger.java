package com.dashjoin.jsonata.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Utils;

/**
 * Port of the jsonata-js 2.2.2 {@code $formatInteger} / {@code $parseInteger} implementation
 * (datetime.js). Numbers are handled as doubles exactly like JavaScript does.
 */
public class FormatInteger {

    private FormatInteger() {
    }

    private static final String[] few = { "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen" };
    private static final String[] ordinals = { "Zeroth", "First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth", "Tenth",
        "Eleventh", "Twelfth", "Thirteenth", "Fourteenth", "Fifteenth", "Sixteenth", "Seventeenth", "Eighteenth", "Nineteenth" };
    private static final String[] decades = { "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety", "Hundred" };
    private static final String[] magnitudes = { "Thousand", "Million", "Billion", "Trillion" };

    /** converts a number into english words */
    static String numberToWords(double value, boolean ordinal) {
        return lookup(value, false, ordinal);
    }

    private static String lookup(double num, boolean prev, boolean ord) {
        String words;
        if (num <= 19) {
            words = (prev ? " and " : "") + (ord ? ordinals[(int) num] : few[(int) num]);
        } else if (num < 100) {
            int tens = (int) Math.floor(num / 10);
            double remainder = num % 10;
            words = (prev ? " and " : "") + decades[tens - 2];
            if (remainder > 0) {
                words += "-" + lookup(remainder, false, ord);
            } else if (ord) {
                words = words.substring(0, words.length() - 1) + "ieth";
            }
        } else if (num < 1000) {
            int hundreds = (int) Math.floor(num / 100);
            double remainder = num % 100;
            words = (prev ? ", " : "") + few[hundreds] + " Hundred";
            if (remainder > 0) {
                words += lookup(remainder, true, ord);
            } else if (ord) {
                words += "th";
            }
        } else {
            int mag = (int) Math.floor(Math.log10(num) / 3);
            if (mag > magnitudes.length) {
                mag = magnitudes.length; // the largest word
            }
            double factor = Math.pow(10, mag * 3);
            double mant = Math.floor(num / factor);
            double remainder = num - mant * factor;
            words = (prev ? ", " : "") + lookup(mant, false, false) + " " + magnitudes[mag - 1];
            if (remainder > 0) {
                words += lookup(remainder, true, ord);
            } else if (ord) {
                words += "th";
            }
        }
        return words;
    }

    private static final Map<String, Double> wordValues = new HashMap<>();
    static {
        for (int i = 0; i < few.length; i++) {
            wordValues.put(few[i].toLowerCase(Locale.ROOT), (double) i);
        }
        for (int i = 0; i < ordinals.length; i++) {
            wordValues.put(ordinals[i].toLowerCase(Locale.ROOT), (double) i);
        }
        for (int i = 0; i < decades.length; i++) {
            String word = decades[i];
            String lword = word.toLowerCase(Locale.ROOT);
            wordValues.put(lword, (double) (i + 2) * 10);
            wordValues.put(lword.substring(0, word.length() - 1) + "ieth", wordValues.get(lword));
        }
        wordValues.put("hundredth", 100.0);
        for (int i = 0; i < magnitudes.length; i++) {
            String lword = magnitudes[i].toLowerCase(Locale.ROOT);
            double val = Math.pow(10, (i + 1) * 3);
            wordValues.put(lword, val);
            wordValues.put(lword + "th", val);
        }
    }

    /** JavaScript \s */
    private static final String JS_WS = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";
    private static final Pattern WORD_SPLIT = Pattern.compile(",[" + JS_WS + "]|[" + JS_WS + "]and[" + JS_WS + "]|[" + JS_WS + "\\\\\\-]");

    /** Converts a number in english words to numeric value */
    static double wordsToNumber(String text) {
        String[] parts = WORD_SPLIT.split(text, -1);
        List<Double> segs = new ArrayList<>();
        segs.add(0.0);
        for (String part : parts) {
            Double value = wordValues.get(part);
            // undefined < 100 is false in JavaScript
            if (value != null && value < 100) {
                double top = segs.remove(segs.size() - 1);
                if (top >= 1000) {
                    segs.add(top);
                    top = 0;
                }
                segs.add(top + value);
            } else {
                double top = segs.remove(segs.size() - 1);
                segs.add(value == null ? Double.NaN : top * value);
            }
        }
        double result = 0;
        for (double d : segs) {
            result += d;
        }
        return result;
    }

    private static final double[] romanNumeralValues = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    private static final String[] romanNumeralLetters = { "m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i" };

    /**
     * jsonata-js converts recursively (one stack frame per numeral) and fails with
     * "Maximum call stack size exceeded" above roughly this value (node 22 default stack)
     */
    private static final double MAX_ROMAN_VALUE = 1e7;

    /** converts a number to roman numerals */
    static String decimalToRoman(double value) {
        if (value > MAX_ROMAN_VALUE) {
            throw new IllegalArgumentException("Maximum call stack size exceeded");
        }
        // iterative version of the recursive jsonata-js implementation
        StringBuilder b = new StringBuilder();
        outer: while (true) {
            for (int index = 0; index < romanNumeralValues.length; index++) {
                if (value >= romanNumeralValues[index]) {
                    b.append(romanNumeralLetters[index]);
                    value = value - romanNumeralValues[index];
                    continue outer;
                }
            }
            return b.toString();
        }
    }

    private static Integer romanValue(char c) {
        switch (c) {
            case 'M': return 1000;
            case 'D': return 500;
            case 'C': return 100;
            case 'L': return 50;
            case 'X': return 10;
            case 'V': return 5;
            case 'I': return 1;
            default: return null;
        }
    }

    /** converts roman numerals to a number */
    static double romanToDecimal(String roman) {
        double decimal = 0;
        double max = 1;
        for (int i = roman.length() - 1; i >= 0; i--) {
            Integer value = romanValue(roman.charAt(i));
            if (value == null || Double.isNaN(max)) {
                // comparisons with undefined are false: max = undefined, decimal = NaN
                if (value == null) {
                    max = Double.NaN;
                    decimal = Double.NaN;
                } else {
                    max = value;
                    decimal += value;
                }
            } else if (value < max) {
                decimal -= value;
            } else {
                max = value;
                decimal += value;
            }
        }
        return decimal;
    }

    /** converts a number to spreadsheet style letters */
    static String decimalToLetters(double value, String aChar) {
        StringBuilder letters = new StringBuilder();
        int aCode = aChar.charAt(0);
        while (value > 0) {
            letters.insert(0, (char) ((value - 1) % 26 + aCode));
            value = Math.floor((value - 1) / 26);
        }
        return letters.toString();
    }

    /** converts spreadsheet style letters to a number */
    static double lettersToDecimal(String letters, char aChar) {
        int aCode = aChar;
        double decimal = 0;
        for (int i = 0; i < letters.length(); i++) {
            decimal += (letters.charAt(letters.length() - i - 1) - aCode + 1) * Math.pow(26, i);
        }
        return decimal;
    }

    /**
     * Formats an integer as specified by the XPath fn:format-integer function
     * See https://www.w3.org/TR/xpath-functions-31/#func-format-integer
     * @param value the number to be formatted
     * @param picture the picture string that specifies the format
     * @return the formatted number
     */
    public static String formatInteger(Number value, String picture) {
        if (value == null) {
            return null;
        }
        double v = Math.floor(value.doubleValue());
        Format format = analyseIntegerPicture(picture);
        return formatInteger(v, format);
    }

    private enum Formats {
        DECIMAL, LETTERS, ROMAN, WORDS, SEQUENCE
    }

    private enum TCase {
        UPPER, LOWER, TITLE
    }

    private static class GroupingSeparator {
        int position;
        String character;

        GroupingSeparator(int position, String character) {
            this.position = position;
            this.character = character;
        }
    }

    private static class Format {
        Formats primary = Formats.DECIMAL;
        TCase tcase = TCase.LOWER;
        boolean ordinal = false;
        int zeroCode;
        int mandatoryDigits;
        boolean regular;
        /** when regular: exactly one entry holding the repeat interval */
        List<GroupingSeparator> groupingSeparators;
        String token;
    }

    private static String formatInteger(double value, Format format) {
        String formattedInteger = "";
        boolean negative = value < 0;
        value = Math.abs(value);
        switch (format.primary) {
            case LETTERS:
                formattedInteger = decimalToLetters(value, format.tcase == TCase.UPPER ? "A" : "a");
                break;
            case ROMAN:
                formattedInteger = decimalToRoman(value);
                if (format.tcase == TCase.UPPER) {
                    formattedInteger = formattedInteger.toUpperCase(Locale.ROOT);
                }
                break;
            case WORDS:
                formattedInteger = numberToWords(value, format.ordinal);
                if (format.tcase == TCase.UPPER) {
                    formattedInteger = formattedInteger.toUpperCase(Locale.ROOT);
                } else if (format.tcase == TCase.LOWER) {
                    formattedInteger = formattedInteger.toLowerCase(Locale.ROOT);
                }
                break;
            case DECIMAL:
                formattedInteger = Utils.jsNumberToString(value);
                int padLength = format.mandatoryDigits - formattedInteger.length();
                if (padLength > 0) {
                    StringBuilder padding = new StringBuilder();
                    for (int i = 0; i < padLength; i++) {
                        padding.append('0');
                    }
                    formattedInteger = padding + formattedInteger;
                }
                if (format.zeroCode != 0x30) {
                    StringBuilder b = new StringBuilder();
                    formattedInteger.codePoints().forEach(cp -> b.appendCodePoint(cp + format.zeroCode - 0x30));
                    formattedInteger = b.toString();
                }
                // insert the grouping-separator-signs, if any
                if (format.regular) {
                    GroupingSeparator sep = format.groupingSeparators.get(0);
                    int n = Math.floorDiv(formattedInteger.length() - 1, sep.position);
                    for (int ii = n; ii > 0; ii--) {
                        int pos = formattedInteger.length() - ii * sep.position;
                        formattedInteger = substr(formattedInteger, 0, pos) + sep.character + substr(formattedInteger, pos);
                    }
                } else {
                    Collections.reverse(format.groupingSeparators);
                    for (GroupingSeparator separator : format.groupingSeparators) {
                        int pos = formattedInteger.length() - separator.position;
                        formattedInteger = substr(formattedInteger, 0, pos) + separator.character + substr(formattedInteger, pos);
                    }
                }

                if (format.ordinal) {
                    char lastDigit = formattedInteger.charAt(formattedInteger.length() - 1);
                    String suffix = lastDigit == '1' ? "st" : lastDigit == '2' ? "nd" : lastDigit == '3' ? "rd" : null;
                    if (suffix == null || (formattedInteger.length() > 1 && formattedInteger.charAt(formattedInteger.length() - 2) == '1')) {
                        suffix = "th";
                    }
                    formattedInteger = formattedInteger + suffix;
                }
                break;
            case SEQUENCE:
                throw new JException("D3130", -1, format.token);
        }
        if (negative) {
            formattedInteger = "-" + formattedInteger;
        }
        return formattedInteger;
    }

    private static final int[] decimalGroups = { 0x30, 0x0660, 0x06F0, 0x07C0, 0x0966, 0x09E6, 0x0A66, 0x0AE6, 0x0B66, 0x0BE6, 0x0C66, 0x0CE6, 0x0D66,
        0x0DE6, 0x0E50, 0x0ED0, 0x0F20, 0x1040, 0x1090, 0x17E0, 0x1810, 0x1946, 0x19D0, 0x1A80, 0x1A90, 0x1B50, 0x1BB0, 0x1C40, 0x1C50, 0xA620, 0xA8D0,
        0xA900, 0xA9D0, 0xA9F0, 0xAA50, 0xABF0, 0xFF10 };

    /** preprocesses the picture string */
    private static Format analyseIntegerPicture(String picture) {
        Format format = new Format();
        String primaryFormat;
        int semicolon = picture.lastIndexOf(';');
        if (semicolon == -1) {
            primaryFormat = picture;
        } else {
            primaryFormat = picture.substring(0, semicolon);
            String formatModifier = picture.substring(semicolon + 1);
            if (formatModifier.startsWith("o")) {
                format.ordinal = true;
            }
        }

        switch (primaryFormat) {
            case "A":
                format.tcase = TCase.UPPER;
                format.primary = Formats.LETTERS;
                break;
            case "a":
                format.primary = Formats.LETTERS;
                break;
            case "I":
                format.tcase = TCase.UPPER;
                format.primary = Formats.ROMAN;
                break;
            case "i":
                format.primary = Formats.ROMAN;
                break;
            case "W":
                format.tcase = TCase.UPPER;
                format.primary = Formats.WORDS;
                break;
            case "Ww":
                format.tcase = TCase.TITLE;
                format.primary = Formats.WORDS;
                break;
            case "w":
                format.primary = Formats.WORDS;
                break;
            default: {
                // this is a decimal-digit-pattern if it contains a decimal digit (from any unicode decimal digit group)
                Integer zeroCode = null;
                int mandatoryDigits = 0;
                List<GroupingSeparator> groupingSeparators = new ArrayList<>();
                int separatorPosition = 0;
                int[] codePoints = primaryFormat.codePoints().toArray();
                // reverse order to determine positions of grouping-separator-signs
                for (int ix = codePoints.length - 1; ix >= 0; ix--) {
                    int codePoint = codePoints[ix];
                    boolean digit = false;
                    for (int group : decimalGroups) {
                        if (codePoint >= group && codePoint <= group + 9) {
                            // codepoint is part of this decimal group
                            digit = true;
                            mandatoryDigits++;
                            separatorPosition++;
                            if (zeroCode == null) {
                                zeroCode = group;
                            } else if (group != zeroCode) {
                                // different decimal groups in the same pattern
                                throw new JException("D3131", -1);
                            }
                            break;
                        }
                    }
                    if (!digit) {
                        if (codePoint == 0x23) { // # - optional-digit-sign
                            separatorPosition++;
                        } else {
                            // assume it is a grouping-separator-sign
                            groupingSeparators.add(new GroupingSeparator(separatorPosition, new String(Character.toChars(codePoint))));
                        }
                    }
                }
                if (mandatoryDigits > 0) {
                    format.primary = Formats.DECIMAL;
                    format.zeroCode = zeroCode;
                    format.mandatoryDigits = mandatoryDigits;
                    int regular = regularRepeat(groupingSeparators);
                    if (regular > 0) {
                        format.regular = true;
                        format.groupingSeparators = new ArrayList<>();
                        format.groupingSeparators.add(new GroupingSeparator(regular, groupingSeparators.get(0).character));
                    } else {
                        format.regular = false;
                        format.groupingSeparators = groupingSeparators;
                    }
                } else {
                    // a 'numbering sequence' (implementation-defined), not supported
                    format.primary = Formats.SEQUENCE;
                    format.token = primaryFormat;
                }
            }
        }
        return format;
    }

    /** are the grouping positions regular? i.e. same interval between each of them */
    private static int regularRepeat(List<GroupingSeparator> separators) {
        if (separators.isEmpty()) {
            return 0;
        }
        String sepChar = separators.get(0).character;
        for (int ii = 1; ii < separators.size(); ii++) {
            if (!separators.get(ii).character.equals(sepChar)) {
                return 0;
            }
        }
        List<Integer> indexes = new ArrayList<>();
        for (GroupingSeparator s : separators) {
            indexes.add(s.position);
        }
        int factor = indexes.get(0);
        for (int i = 1; i < indexes.size(); i++) {
            factor = gcd(factor, indexes.get(i));
        }
        for (int index = 1; index <= indexes.size(); index++) {
            if (!indexes.contains(index * factor)) {
                return 0;
            }
        }
        return factor;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /**
     * parse a string containing an integer as specified by the picture string
     * @param value the string to parse
     * @param picture the picture string
     * @return the parsed number
     */
    public static Number parseInteger(String value, String picture) {
        if (value == null) {
            return null;
        }
        Format formatSpec = analyseIntegerPicture(picture);
        double result;
        boolean isUpper = formatSpec.tcase == TCase.UPPER;
        switch (formatSpec.primary) {
            case LETTERS:
                result = lettersToDecimal(value, isUpper ? 'A' : 'a');
                break;
            case ROMAN:
                result = romanToDecimal(isUpper ? value : value.toUpperCase(Locale.ROOT));
                break;
            case WORDS:
                result = wordsToNumber(value.toLowerCase(Locale.ROOT));
                break;
            case DECIMAL: {
                String digits = value;
                if (formatSpec.ordinal) {
                    // strip off the suffix
                    digits = jsSubstring(value, 0, value.length() - 2);
                }
                // strip out the separators
                if (formatSpec.regular) {
                    digits = digits.replace(",", "");
                } else {
                    for (GroupingSeparator sep : formatSpec.groupingSeparators) {
                        digits = splitJoin(digits, sep.character);
                    }
                }
                if (formatSpec.zeroCode != 0x30) {
                    // apply offset
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < digits.length(); i++) {
                        int cp = digits.charAt(i) - formatSpec.zeroCode + 0x30;
                        if (cp < 0) {
                            throw new IllegalArgumentException("Invalid code point " + cp);
                        }
                        b.appendCodePoint(cp);
                    }
                    digits = b.toString();
                }
                result = parseInt(digits);
                break;
            }
            case SEQUENCE:
            default:
                throw new JException("D3130", -1, formatSpec.token);
        }
        return result;
    }

    /** digits.split(sep).join('') */
    private static String splitJoin(String s, String sep) {
        if (sep.isEmpty()) {
            return s;
        }
        return s.replace(sep, "");
    }

    private static boolean isJsWhitespace(char c) {
        return c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == ' ' || c == 0xA0 || c == 0x1680
            || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000 || c == 0xFEFF;
    }

    /** ECMAScript parseInt(string) without radix */
    static double parseInt(String s) {
        int i = 0;
        int len = s.length();
        while (i < len && isJsWhitespace(s.charAt(i))) {
            i++;
        }
        boolean neg = false;
        if (i < len && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        int radix = 10;
        if (i + 1 < len && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
            radix = 16;
            i += 2;
        }
        int start = i;
        while (i < len && Character.digit(s.charAt(i), radix) >= 0 && s.charAt(i) < 0x80) {
            i++;
        }
        if (i == start) {
            return Double.NaN;
        }
        double d = new BigInteger(s.substring(start, i), radix).doubleValue();
        return neg ? -d : d;
    }

    /** ECMAScript String.prototype.substr */
    private static String substr(String s, int start) {
        return substr(s, start, Integer.MAX_VALUE);
    }

    private static String substr(String s, int start, int length) {
        int size = s.length();
        int a = start < 0 ? Math.max(size + start, 0) : Math.min(start, size);
        int l = Math.min(Math.max(length, 0), size - a);
        return l <= 0 ? "" : s.substring(a, a + l);
    }

    /** ECMAScript String.prototype.substring */
    private static String jsSubstring(String s, int start, int end) {
        int len = s.length();
        int a = Math.min(Math.max(start, 0), len);
        int b = Math.min(Math.max(end, 0), len);
        return s.substring(Math.min(a, b), Math.max(a, b));
    }
}
