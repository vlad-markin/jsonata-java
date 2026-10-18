package com.dashjoin.jsonata.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Utils;

/**
 * Port of jsonata-js 2.2.2 {@code formatNumber} (XPath F&amp;O fn:format-number) including the
 * JavaScript string and number primitives it relies on, so that results match jsonata-js exactly.
 */
public class FormatNumber {

    private FormatNumber() {
    }

    /** The parts of a sub-picture, F&amp;O 4.7.3 */
    private static class Parts {
        String prefix, suffix, activePart, mantissaPart, exponentPart, integerPart, fractionalPart, subpicture;
    }

    /** The variables of a sub-picture, F&amp;O 4.7.4 */
    private static class Variables {
        List<Integer> integerPartGroupingPositions;
        int regularGrouping;
        int minimumIntegerPartSize;
        int scalingFactor;
        String prefix;
        List<Integer> fractionalPartGroupingPositions;
        int minimumFactionalPartSize;
        int maximumFactionalPartSize;
        int minimumExponentSize;
        String suffix;
        String picture;

        Variables copy() {
            Variables v = new Variables();
            v.integerPartGroupingPositions = new ArrayList<>(integerPartGroupingPositions);
            v.regularGrouping = regularGrouping;
            v.minimumIntegerPartSize = minimumIntegerPartSize;
            v.scalingFactor = scalingFactor;
            v.prefix = prefix;
            v.fractionalPartGroupingPositions = new ArrayList<>(fractionalPartGroupingPositions);
            v.minimumFactionalPartSize = minimumFactionalPartSize;
            v.maximumFactionalPartSize = maximumFactionalPartSize;
            v.minimumExponentSize = minimumExponentSize;
            v.suffix = suffix;
            v.picture = picture;
            return v;
        }
    }

    /**
     * Formats a number into a decimal string representation using XPath 3.1 F&amp;O fn:format-number spec
     * @param value number to format
     * @param picture picture string definition
     * @param options override locale defaults
     * @return the formatted string
     */
    public static String formatNumber(Number value, String picture, Map<?, ?> options) {
        // undefined inputs always return undefined
        if (value == null) {
            return null;
        }
        final double val = value.doubleValue();

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("decimal-separator", ".");
        properties.put("grouping-separator", ",");
        properties.put("exponent-separator", "e");
        properties.put("infinity", "Infinity");
        properties.put("minus-sign", "-");
        properties.put("NaN", "NaN");
        properties.put("percent", "%");
        properties.put("per-mille", "‰");
        properties.put("zero-digit", "0");
        properties.put("digit", "#");
        properties.put("pattern-separator", ";");

        // if `options` is specified, then its entries override defaults
        if (options != null) {
            for (Map.Entry<?, ?> e : options.entrySet()) {
                properties.put(String.valueOf(e.getKey()), jsString(e.getValue()));
            }
        }

        final String decimalSeparator = properties.get("decimal-separator");
        final String groupingSeparator = properties.get("grouping-separator");
        final String exponentSeparator = properties.get("exponent-separator");
        final String percent = properties.get("percent");
        final String perMille = properties.get("per-mille");
        final String zeroDigit = properties.get("zero-digit");
        final String digit = properties.get("digit");
        final String patternSeparator = properties.get("pattern-separator");
        final String minusSign = properties.get("minus-sign");

        final List<String> decimalDigitFamily = new ArrayList<>();
        if (!zeroDigit.isEmpty()) {
            int zeroCharCode = zeroDigit.charAt(0);
            for (int ii = zeroCharCode; ii < zeroCharCode + 10; ii++) {
                decimalDigitFamily.add(String.valueOf((char) ii));
            }
        }

        final List<String> activeChars = new ArrayList<>(decimalDigitFamily);
        activeChars.add(decimalSeparator);
        activeChars.add(exponentSeparator);
        activeChars.add(groupingSeparator);
        activeChars.add(digit);
        activeChars.add(patternSeparator);

        List<String> subPictures = split(picture, patternSeparator);

        if (subPictures.size() > 2) {
            throw new JException("D3080", -1);
        }

        List<Parts> partsList = new ArrayList<>();
        for (String subpicture : subPictures) {
            partsList.add(splitParts(subpicture, activeChars, exponentSeparator, decimalSeparator));
        }
        for (Parts parts : partsList) {
            validate(parts, properties, decimalDigitFamily, activeChars);
        }
        List<Variables> variables = new ArrayList<>();
        for (Parts parts : partsList) {
            variables.add(analyse(parts, groupingSeparator, digit, decimalDigitFamily));
        }

        if (variables.size() == 1) {
            Variables neg = variables.get(0).copy();
            neg.prefix = minusSign + neg.prefix;
            variables.add(neg);
        }

        // format the number
        // bullet 1: TODO: NaN - not sure we'd ever get this in JSON
        Variables pic;
        // bullet 2:
        if (val >= 0) {
            pic = variables.get(0);
        } else {
            pic = variables.get(1);
        }
        double adjustedNumber;
        // bullet 3:
        if (pic.picture.indexOf(percent) != -1) {
            adjustedNumber = val * 100;
        } else if (pic.picture.indexOf(perMille) != -1) {
            adjustedNumber = val * 1000;
        } else {
            adjustedNumber = val;
        }
        // bullet 4: TODO: infinity - not sure we'd ever get this in JSON
        // bullet 5:
        double mantissa;
        Integer exponent = null;
        if (pic.minimumExponentSize == 0) {
            mantissa = adjustedNumber;
        } else {
            // mantissa * 10^exponent = adjustedNumber
            double maxMantissa = Math.pow(10, pic.scalingFactor);
            double minMantissa = Math.pow(10, pic.scalingFactor - 1);
            mantissa = adjustedNumber;
            int exp = 0;
            if (mantissa != 0 && Double.isFinite(mantissa)) {
                while (Math.abs(mantissa) < minMantissa) {
                    mantissa *= 10;
                    exp -= 1;
                }
                while (Math.abs(mantissa) > maxMantissa) {
                    mantissa /= 10;
                    exp += 1;
                }
            }
            exponent = exp;
        }
        // bullet 6:
        double roundedNumber = round(mantissa, pic.maximumFactionalPartSize);
        // bullet 7:
        String stringValue = makeString(roundedNumber, pic.maximumFactionalPartSize, zeroDigit, decimalDigitFamily);
        int decimalPos = stringValue.indexOf('.');
        if (decimalPos == -1) {
            stringValue = stringValue + decimalSeparator;
        } else {
            stringValue = stringValue.substring(0, decimalPos) + decimalSeparator + stringValue.substring(decimalPos + 1);
        }
        while (!stringValue.isEmpty() && charAt(stringValue, 0).equals(zeroDigit)) {
            stringValue = substring(stringValue, 1);
        }
        while (!stringValue.isEmpty() && charAt(stringValue, stringValue.length() - 1).equals(zeroDigit)) {
            stringValue = substring(stringValue, 0, stringValue.length() - 1);
        }
        // bullets 8 & 9:
        decimalPos = stringValue.indexOf(decimalSeparator);
        int padLeft = pic.minimumIntegerPartSize - decimalPos;
        int padRight = pic.minimumFactionalPartSize - (stringValue.length() - decimalPos - 1);
        stringValue = (padLeft > 0 ? repeat(zeroDigit, padLeft) : "") + stringValue;
        stringValue = stringValue + (padRight > 0 ? repeat(zeroDigit, padRight) : "");
        decimalPos = stringValue.indexOf(decimalSeparator);
        // bullet 10:
        if (pic.regularGrouping > 0) {
            int groupCount = Math.floorDiv(decimalPos - 1, pic.regularGrouping);
            for (int group = 1; group <= groupCount; group++) {
                int at = decimalPos - group * pic.regularGrouping;
                stringValue = slice(stringValue, 0, at) + groupingSeparator + slice(stringValue, at);
            }
        } else {
            for (int pos : pic.integerPartGroupingPositions) {
                stringValue = slice(stringValue, 0, decimalPos - pos) + groupingSeparator + slice(stringValue, decimalPos - pos);
                decimalPos++;
            }
        }
        // bullet 11:
        decimalPos = stringValue.indexOf(decimalSeparator);
        for (int pos : pic.fractionalPartGroupingPositions) {
            stringValue = slice(stringValue, 0, pos + decimalPos + 1) + groupingSeparator + slice(stringValue, pos + decimalPos + 1);
        }
        // bullet 12:
        decimalPos = stringValue.indexOf(decimalSeparator);
        if (pic.picture.indexOf(decimalSeparator) == -1 || decimalPos == stringValue.length() - 1) {
            stringValue = substring(stringValue, 0, stringValue.length() - 1);
        }
        // bullet 13:
        if (exponent != null) {
            String stringExponent = makeString(exponent, 0, zeroDigit, decimalDigitFamily);
            padLeft = pic.minimumExponentSize - stringExponent.length();
            if (padLeft > 0) {
                stringExponent = repeat(zeroDigit, padLeft) + stringExponent;
            }
            stringValue = stringValue + exponentSeparator + (exponent < 0 ? minusSign : "") + stringExponent;
        }
        // bullet 14:
        stringValue = pic.prefix + stringValue + pic.suffix;
        return stringValue;
    }

    private static Parts splitParts(String subpicture, List<String> activeChars, String exponentSeparator, String decimalSeparator) {
        String prefix = "";
        for (int ii = 0; ii < subpicture.length(); ii++) {
            String ch = charAt(subpicture, ii);
            if (activeChars.contains(ch) && !ch.equals(exponentSeparator)) {
                prefix = subpicture.substring(0, ii);
                break;
            }
        }
        String suffix = "";
        for (int ii = subpicture.length() - 1; ii >= 0; ii--) {
            String ch = charAt(subpicture, ii);
            if (activeChars.contains(ch) && !ch.equals(exponentSeparator)) {
                suffix = subpicture.substring(ii + 1);
                break;
            }
        }
        String activePart = substring(subpicture, prefix.length(), subpicture.length() - suffix.length());
        String mantissaPart, exponentPart, integerPart, fractionalPart;
        int exponentPosition = subpicture.indexOf(exponentSeparator, prefix.length());
        if (exponentPosition == -1 || exponentPosition > subpicture.length() - suffix.length()) {
            mantissaPart = activePart;
            exponentPart = null;
        } else {
            // note: jsonata-js applies the position within the sub-picture to the active part
            mantissaPart = substring(activePart, 0, exponentPosition);
            exponentPart = substring(activePart, exponentPosition + 1);
        }
        int decimalPosition = mantissaPart.indexOf(decimalSeparator);
        if (decimalPosition == -1) {
            integerPart = mantissaPart;
            fractionalPart = suffix;
        } else {
            integerPart = substring(mantissaPart, 0, decimalPosition);
            fractionalPart = substring(mantissaPart, decimalPosition + 1);
        }
        Parts p = new Parts();
        p.prefix = prefix;
        p.suffix = suffix;
        p.activePart = activePart;
        p.mantissaPart = mantissaPart;
        p.exponentPart = exponentPart;
        p.integerPart = integerPart;
        p.fractionalPart = fractionalPart;
        p.subpicture = subpicture;
        return p;
    }

    // validate the picture string, F&O 4.7.3
    private static void validate(Parts parts, Map<String, String> properties, List<String> decimalDigitFamily, List<String> activeChars) {
        String error = null;
        String subpicture = parts.subpicture;
        String decimalSeparator = properties.get("decimal-separator");
        String groupingSeparator = properties.get("grouping-separator");
        String percent = properties.get("percent");
        String perMille = properties.get("per-mille");
        String digit = properties.get("digit");
        int decimalPos = subpicture.indexOf(decimalSeparator);
        if (decimalPos != subpicture.lastIndexOf(decimalSeparator)) {
            error = "D3081";
        }
        if (subpicture.indexOf(percent) != subpicture.lastIndexOf(percent)) {
            error = "D3082";
        }
        if (subpicture.indexOf(perMille) != subpicture.lastIndexOf(perMille)) {
            error = "D3083";
        }
        if (subpicture.indexOf(percent) != -1 && subpicture.indexOf(perMille) != -1) {
            error = "D3084";
        }
        boolean valid = false;
        for (int ii = 0; ii < parts.mantissaPart.length(); ii++) {
            String ch = charAt(parts.mantissaPart, ii);
            if (decimalDigitFamily.contains(ch) || ch.equals(digit)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            error = "D3085";
        }
        for (int ii = 0; ii < parts.activePart.length(); ii++) {
            if (!activeChars.contains(charAt(parts.activePart, ii))) {
                error = "D3086";
                break;
            }
        }
        if (decimalPos != -1) {
            if (charAt(subpicture, decimalPos - 1).equals(groupingSeparator) || charAt(subpicture, decimalPos + 1).equals(groupingSeparator)) {
                error = "D3087";
            }
        } else if (charAt(parts.integerPart, parts.integerPart.length() - 1).equals(groupingSeparator)) {
            error = "D3088";
        }
        if (subpicture.indexOf(groupingSeparator + groupingSeparator) != -1) {
            error = "D3089";
        }
        int optionalDigitPos = parts.integerPart.indexOf(digit);
        if (optionalDigitPos != -1 && count(substring(parts.integerPart, 0, optionalDigitPos), decimalDigitFamily, null) > 0) {
            error = "D3090";
        }
        optionalDigitPos = parts.fractionalPart.lastIndexOf(digit);
        if (optionalDigitPos != -1 && count(substring(parts.fractionalPart, optionalDigitPos), decimalDigitFamily, null) > 0) {
            error = "D3091";
        }
        boolean exponentExists = parts.exponentPart != null;
        if (exponentExists && parts.exponentPart.length() > 0 && (subpicture.indexOf(percent) != -1 || subpicture.indexOf(perMille) != -1)) {
            error = "D3092";
        }
        if (exponentExists && (parts.exponentPart.length() == 0
                || parts.exponentPart.length() - count(parts.exponentPart, decimalDigitFamily, null) > 0)) {
            error = "D3093";
        }
        if (error != null) {
            throw new JException(error, -1);
        }
    }

    // analyse the picture string, F&O 4.7.4
    private static Variables analyse(Parts parts, String groupingSeparator, String digit, List<String> decimalDigitFamily) {
        List<Integer> integerPartGroupingPositions = getGroupingPositions(parts, parts.integerPart, false, groupingSeparator, digit, decimalDigitFamily);
        int regularGrouping = regular(integerPartGroupingPositions);
        List<Integer> fractionalPartGroupingPositions = getGroupingPositions(parts, parts.fractionalPart, true, groupingSeparator, digit, decimalDigitFamily);

        int minimumIntegerPartSize = count(parts.integerPart, decimalDigitFamily, null);
        int scalingFactor = minimumIntegerPartSize;

        int minimumFactionalPartSize = count(parts.fractionalPart, decimalDigitFamily, null);
        int maximumFactionalPartSize = count(parts.fractionalPart, decimalDigitFamily, digit);
        boolean exponentPresent = parts.exponentPart != null;
        if (minimumIntegerPartSize == 0 && maximumFactionalPartSize == 0) {
            if (exponentPresent) {
                minimumFactionalPartSize = 1;
                maximumFactionalPartSize = 1;
            } else {
                minimumIntegerPartSize = 1;
            }
        }
        if (exponentPresent && minimumIntegerPartSize == 0 && parts.integerPart.indexOf(digit) != -1) {
            minimumIntegerPartSize = 1;
        }
        if (minimumIntegerPartSize == 0 && minimumFactionalPartSize == 0) {
            minimumFactionalPartSize = 1;
        }
        int minimumExponentSize = 0;
        if (exponentPresent) {
            minimumExponentSize = count(parts.exponentPart, decimalDigitFamily, null);
        }

        Variables v = new Variables();
        v.integerPartGroupingPositions = integerPartGroupingPositions;
        v.regularGrouping = regularGrouping;
        v.minimumIntegerPartSize = minimumIntegerPartSize;
        v.scalingFactor = scalingFactor;
        v.prefix = parts.prefix;
        v.fractionalPartGroupingPositions = fractionalPartGroupingPositions;
        v.minimumFactionalPartSize = minimumFactionalPartSize;
        v.maximumFactionalPartSize = maximumFactionalPartSize;
        v.minimumExponentSize = minimumExponentSize;
        v.suffix = parts.suffix;
        v.picture = parts.subpicture;
        return v;
    }

    private static List<Integer> getGroupingPositions(Parts parts, String part, boolean toLeft, String groupingSeparator, String digit, List<String> decimalDigitFamily) {
        List<Integer> positions = new ArrayList<>();
        int groupingPosition = part.indexOf(groupingSeparator);
        while (groupingPosition != -1) {
            String chars = toLeft ? substring(part, 0, groupingPosition) : substring(part, groupingPosition);
            positions.add(count(chars, decimalDigitFamily, digit));
            // note: jsonata-js always searches the integer part for the next separator
            groupingPosition = parts.integerPart.indexOf(groupingSeparator, groupingPosition + 1);
        }
        return positions;
    }

    // are the grouping positions regular? i.e. same interval between each of them
    private static int regular(List<Integer> indexes) {
        if (indexes.isEmpty()) {
            return 0;
        }
        // find the greatest common divisor of all the positions
        int factor = indexes.get(0);
        for (int i = 1; i < indexes.size(); i++) {
            factor = gcd(factor, indexes.get(i));
        }
        // is every position separated by this divisor? If so, it's regular
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

    /** number of chars of s that are members of the digit family (or equal to the optional digit) */
    private static int count(String s, List<String> decimalDigitFamily, String digit) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = charAt(s, i);
            if (decimalDigitFamily.contains(ch) || (digit != null && ch.equals(digit))) {
                n++;
            }
        }
        return n;
    }

    private static String makeString(double value, int dp, String zeroDigit, List<String> decimalDigitFamily) {
        String str = toFixed(Math.abs(value), dp);
        if (!zeroDigit.equals("0")) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c >= '0' && c <= '9') {
                    int idx = c - '0';
                    if (idx < decimalDigitFamily.size()) {
                        b.append(decimalDigitFamily.get(idx));
                    }
                } else {
                    b.append(c);
                }
            }
            str = b.toString();
        }
        return str;
    }

    // ---------------------------------------------------------------------------------------------
    // JavaScript primitives

    /** ToString of an option value */
    private static String jsString(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof Number) {
            return Utils.jsNumberToString(((Number) o).doubleValue());
        }
        return o.toString();
    }

    /** jsonata-js round (half even, decimal shifting done via the string representation) */
    public static double round(double arg, int precision) {
        if (precision != 0) {
            arg = shift(arg, precision);
        }
        // round up to nearest int
        double result = mathRound(arg);
        double diff = result - arg;
        if (Math.abs(diff) == 0.5 && Math.abs(result % 2) == 1) {
            // rounded the wrong way - adjust to nearest even number
            result = result - 1;
        }
        if (precision != 0) {
            result = shift(result, -precision);
        }
        if (result == 0) {
            // JSON doesn't do -0
            result = 0;
        }
        return result;
    }

    /** +(value[0] + 'e' + (value[1] ? (+value[1] + precision) : precision)) */
    private static double shift(double arg, int precision) {
        if (!Double.isFinite(arg)) {
            return Double.NaN;
        }
        String s = Utils.jsNumberToString(arg);
        int e = s.indexOf('e');
        String mant = e < 0 ? s : s.substring(0, e);
        long exp = e < 0 ? precision : Long.parseLong(s.substring(e + 1).replace("+", "")) + precision;
        return Double.parseDouble(mant + "e" + exp);
    }

    /** ECMAScript Math.round */
    public static double mathRound(double x) {
        if (!Double.isFinite(x) || x == 0) {
            return x;
        }
        double f = Math.floor(x);
        double r = (x - f >= 0.5) ? f + 1 : f;
        if (r == 0 && x < 0) {
            return -0.0;
        }
        return r;
    }

    /** ECMAScript Number.prototype.toFixed */
    public static String toFixed(double x, int fractionDigits) {
        if (fractionDigits < 0 || fractionDigits > 100) {
            throw new IllegalArgumentException("toFixed() digits argument must be between 0 and 100");
        }
        if (Double.isNaN(x)) {
            return "NaN";
        }
        if (Math.abs(x) >= 1e21 || Double.isInfinite(x)) {
            return Utils.jsNumberToString(x);
        }
        if (x < 0) {
            return "-" + toFixed(-x, fractionDigits);
        }
        // pick n for which n / 10^f - x is closest to zero; the larger n on a tie
        return new BigDecimal(x).setScale(fractionDigits, RoundingMode.HALF_UP).toPlainString();
    }

    /** ECMAScript String.prototype.charAt */
    private static String charAt(String s, int i) {
        return i < 0 || i >= s.length() ? "" : String.valueOf(s.charAt(i));
    }

    /** ECMAScript String.prototype.substring */
    private static String substring(String s, int start) {
        return substring(s, start, s.length());
    }

    private static String substring(String s, int start, int end) {
        int len = s.length();
        int a = Math.min(Math.max(start, 0), len);
        int b = Math.min(Math.max(end, 0), len);
        return s.substring(Math.min(a, b), Math.max(a, b));
    }

    /** ECMAScript String.prototype.slice */
    private static String slice(String s, int start) {
        return slice(s, start, s.length());
    }

    private static String slice(String s, int start, int end) {
        int len = s.length();
        int a = start < 0 ? Math.max(len + start, 0) : Math.min(start, len);
        int b = end < 0 ? Math.max(len + end, 0) : Math.min(end, len);
        return a < b ? s.substring(a, b) : "";
    }

    /** ECMAScript String.prototype.split with a string separator */
    private static List<String> split(String s, String sep) {
        List<String> res = new ArrayList<>();
        if (sep.isEmpty()) {
            for (int i = 0; i < s.length(); i++) {
                res.add(String.valueOf(s.charAt(i)));
            }
            return res;
        }
        int start = 0;
        int idx;
        while ((idx = s.indexOf(sep, start)) != -1) {
            res.add(s.substring(start, idx));
            start = idx + sep.length();
        }
        res.add(s.substring(start));
        return res;
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(s);
        }
        return b.toString();
    }
}
