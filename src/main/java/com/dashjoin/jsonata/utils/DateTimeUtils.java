/**
 * jsonata-java is the JSONata Java reference port
 * 
 * Copyright Dashjoin GmbH. https://dashjoin.com
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

// Derived from original JSONata4Java DateTimeUtils code under this license:
/*
 * (c) Copyright 2018, 2019 IBM Corporation
 * 1 New Orchard Road, 
 * Armonk, New York, 10504-1722
 * United States
 * +1 914 499 1900
 * support: Nathaniel Mills wnm3@us.ibm.com
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
 *
 */
package com.dashjoin.jsonata.utils;
//package com.api.jsonata4java.expressions.utils;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
// import org.apache.commons.lang3.ArrayUtils;
// import org.apache.commons.lang3.StringUtils;
// import org.apache.commons.lang3.tuple.ImmutablePair;
// import org.apache.commons.lang3.tuple.Pair;

import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Utils;

public class DateTimeUtils implements Serializable {

    private static final long serialVersionUID = 365963860104380193L;

    private static String[] few = {
        "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static String[] ordinals = {
        "Zeroth", "First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth", "Tenth",
        "Eleventh", "Twelfth", "Thirteenth", "Fourteenth", "Fifteenth", "Sixteenth", "Seventeenth", "Eighteenth", "Nineteenth"
    };
    private static String[] decades = {
        "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety", "Hundred"
    };
    private static String[] magnitudes = {
        "Thousand", "Million", "Billion", "Trillion"
    };

    public static String numberToWords(long value, boolean ordinal) {
        return lookup(value, false, ordinal);
    }

    private static String lookup(long num, boolean prev, boolean ord) {
        String words = "";
        if (num <= 19) {
            words = (prev ? " and " : "") + (ord ? ordinals[(int)num] : few[(int)num]);
        } else if (num < 100) {
            int tens = (int)num / 10;
            int remainder = (int)num % 10;
            words = (prev ? " and " : "") + decades[tens - 2];
            if (remainder > 0) {
                words += "-" + lookup(remainder, false, ord);
            } else if (ord) {
                words = words.substring(0, words.length() - 1) + "ieth";
            }
        } else if (num < 1000) {
            int hundreds = (int)num / 100;
            int remainder = (int)num % 100;
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
            long factor = (long)Math.pow(10, mag * 3);
            int mant = (int) Math.floor(num / factor);
            long remainder = num - mant * factor;
            words = (prev ? ", " : "") + lookup(mant, false, false) + " " + magnitudes[mag - 1];
            if (remainder > 0) {
                words += lookup(remainder, true, ord);
            } else if (ord) {
                words += "th";
            }
        }
        return words;
    }

    private static Map<String, Long> wordValuesLong = new HashMap<>();
    static {
        for (int i = 0; i < few.length; i++) {
          wordValuesLong.put(few[i].toLowerCase(), (long)i);
        }
        for (int i = 0; i < ordinals.length; i++) {
          wordValuesLong.put(ordinals[i].toLowerCase(), (long)i);
        }
        for (int i = 0; i < decades.length; i++) {
            String lword = decades[i].toLowerCase();
            wordValuesLong.put(lword, (long)(i + 2) * 10);
            wordValuesLong.put(lword.substring(0, lword.length() - 1) + "ieth", wordValuesLong.get(lword));
        }
        wordValuesLong.put("hundredth", (long)100);
        wordValuesLong.put("hundreth", (long)100);
        for (int i = 0; i < magnitudes.length; i++) {
            String lword = magnitudes[i].toLowerCase();
            long val = (long) Math.pow(10, (i + 1) * 3);
            wordValuesLong.put(lword, val);
            wordValuesLong.put(lword + "th", val);
        }
    }

    /**
     * parses english words to a long value
     */
    public static long wordsToLong(String text) {
      String[] parts = text.split(",\\s|\\sand\\s|[\\s\\-]");
      Long[] values = new Long[parts.length];
      for (int i = 0; i < parts.length; i++) {
          values[i] = wordValuesLong.get(parts[i]);
      }
      Stack<Long> segs = new Stack<>();
      segs.push((long)0);
      for (Long value : values) {
          if (value < 100) {
              Long top = segs.pop();
              if (top >= 1000) {
                  segs.push(top);
                  top = (long)0;
              }
              segs.push(top + value);
          } else {
              segs.push(segs.pop() * value);
          }
      }
      return segs.stream().mapToLong(i -> i.longValue()).sum();
  }

    private static class RomanNumeral {

        private int value;
        private String letters;

        public RomanNumeral(int value, String letters) {
            this.value = value;
            this.letters = letters;
        }

        public int getValue() {
            return this.value;
        }

        public String getLetters() {
            return this.letters;
        }
    }

    private static RomanNumeral[] romanNumerals = {
        new RomanNumeral(1000, "m"),
        new RomanNumeral(900, "cm"),
        new RomanNumeral(500, "d"),
        new RomanNumeral(400, "cd"),
        new RomanNumeral(100, "c"),
        new RomanNumeral(90, "xc"),
        new RomanNumeral(50, "l"),
        new RomanNumeral(40, "xl"),
        new RomanNumeral(10, "x"),
        new RomanNumeral(9, "ix"),
        new RomanNumeral(5, "v"),
        new RomanNumeral(4, "iv"),
        new RomanNumeral(1, "i")
    };

    private static Map<String, Integer> romanValues = createRomanValues();

    private static Map<String, Integer> createRomanValues() {
        Map<String, Integer> values = new HashMap<>();
        values.put("M", 1000);
        values.put("D", 500);
        values.put("C", 100);
        values.put("L", 50);
        values.put("X", 10);
        values.put("V", 5);
        values.put("I", 1);
        return values;
    }

    public static int romanToDecimal(String roman) {
        int decimal = 0;
        int max = 1;
        for (int i = roman.length() - 1; i >= 0; i--) {
            String digit = Character.toString(roman.charAt(i));
            int value = romanValues.get(digit);
            if (value < max) {
                decimal -= value;
            } else {
                max = value;
                decimal += value;
            }
        }
        return decimal;
    }

    public static String formatInteger(long value, String picture) {
        Format format = analyseIntegerPicture(picture);
        return formatInteger((double) value, format);
    }

    /** jsonata-js formatInteger(value, picture) on a JavaScript number */
    static String formatInteger(double value, String picture) {
        return formatInteger(Math.floor(value), analyseIntegerPicture(picture));
    }

    enum formats {

            DECIMAL("decimal"), LETTERS("letters"), ROMAN("roman"), WORDS("words"), SEQUENCE("sequence");

        public String value;

        private formats(String value) {
            this.value = value;
        }
    }

    enum tcase {

            UPPER("upper"), LOWER("lower"), TITLE("title");

        public String value;

        private tcase(String value) {
            this.value = value;
        }
    }

    private static class Format {

        @SuppressWarnings("unused")
        public String type = "integer";
        public formats primary = formats.DECIMAL;
        public tcase case_type = tcase.LOWER;
        public boolean ordinal = false;
        public int zeroCode = 0;
        /** a JavaScript number (NaN if set from an invalid width modifier) */
        public double mandatoryDigits = 0;
        public int optionalDigits = 0;
        /** exact number of digits to parse (null = any) */
        public Double parseWidth;
        public boolean regular = false;
        public List<GroupingSeparator> groupingSeparators = new ArrayList<>();
        public String token;
    }

    private static class GroupingSeparator {

        public int position;
        public String character;

        public GroupingSeparator(int position, String character) {
            this.position = position;
            this.character = character;
        }
    }

    /** a JavaScript TypeError / RangeError: not a JSONata error */
    private static RuntimeException jsError(String message) {
        return new IllegalStateException(message);
    }

    /** String.prototype.substr(start, length) */
    private static String jsSubstr(String s, int start, Integer length) {
        int len = s.length();
        if (start < 0)
            start = Math.max(len + start, 0);
        start = Math.min(start, len);
        int end = length == null ? len : Math.min(start + Math.max(length, 0), len);
        return s.substring(start, end);
    }

    /** String.prototype.substring(start, end) */
    private static String jsSubstring(String s, double start, double end) {
        int len = s.length();
        int a = (int) Math.min(Math.max(Double.isNaN(start) ? 0 : start, 0), len);
        int b = (int) Math.min(Math.max(Double.isNaN(end) ? 0 : end, 0), len);
        return s.substring(Math.min(a, b), Math.max(a, b));
    }

    /** JavaScript parseInt(s) (without radix) */
    static double jsParseInt(String s) {
        int i = 0, n = s.length();
        while (i < n && (JS_WHITESPACE.indexOf(s.charAt(i)) >= 0))
            i++;
        double sign = 1;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            if (s.charAt(i) == '-')
                sign = -1;
            i++;
        }
        int radix = 10;
        if (i + 1 < n && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
            radix = 16;
            i += 2;
        }
        int start = i;
        while (i < n && Character.digit(s.charAt(i), radix) >= 0 && s.charAt(i) < 128)
            i++;
        if (i == start)
            return Double.NaN;
        String digits = s.substring(start, i);
        double v;
        if (radix == 10)
            v = Double.parseDouble(digits);
        else
            v = new BigInteger(digits, 16).doubleValue();
        return sign * v;
    }

    private static final String JS_WHITESPACE = "\t\n\u000B\f\r                  　﻿";

    private static boolean truthy(Double d) {
        return d != null && d != 0 && !d.isNaN();
    }

    private static String decimalToLetters(double value, char aChar) {
        StringBuilder letters = new StringBuilder();
        while (value > 0) {
            letters.insert(0, (char) ((value - 1) % 26 + aChar));
            value = Math.floor((value - 1) / 26);
        }
        return letters.toString();
    }

    private static String decimalToRoman(double value) {
        if (value / 1000 > 10000)
            // the recursive jsonata-js implementation exceeds the call stack
            throw jsError("Maximum call stack size exceeded");
        StringBuilder b = new StringBuilder();
        boolean found = true;
        while (found) {
            found = false;
            for (RomanNumeral numeral : romanNumerals) {
                if (value >= numeral.getValue()) {
                    b.append(numeral.getLetters());
                    value -= numeral.getValue();
                    found = true;
                    break;
                }
            }
        }
        return b.toString();
    }

    private static String numberToWords(double value, boolean ordinal) {
        if (Double.isNaN(value))
            // the recursive jsonata-js implementation does not terminate
            throw jsError("Maximum call stack size exceeded");
        return numberToWords((long) value, ordinal);
    }

    private static String formatInteger(double value, Format format) {
        String formattedInteger = "";
        boolean negative = value < 0;
        value = Math.abs(value);
        switch (format.primary) {
            case LETTERS:
                formattedInteger = decimalToLetters(value, format.case_type == tcase.UPPER ? 'A' : 'a');
                break;
            case ROMAN:
                formattedInteger = decimalToRoman(value);
                if (format.case_type == tcase.UPPER) {
                    formattedInteger = formattedInteger.toUpperCase();
                }
                break;
            case WORDS:
                formattedInteger = numberToWords(value, format.ordinal);
                if (format.case_type == tcase.UPPER) {
                    formattedInteger = formattedInteger.toUpperCase();
                } else if (format.case_type == tcase.LOWER) {
                    formattedInteger = formattedInteger.toLowerCase();
                }
                break;
            case DECIMAL:
                formattedInteger = Utils.jsNumberToString(value);
                double padLength = format.mandatoryDigits - formattedInteger.length();
                if (padLength > 0) {
                    StringBuilder pad = new StringBuilder();
                    for (int i = 0; i < padLength; i++)
                        pad.append('0');
                    formattedInteger = pad + formattedInteger;
                }
                if (format.zeroCode != 0x30) {
                    StringBuilder b = new StringBuilder();
                    final int offset = format.zeroCode - 0x30;
                    formattedInteger.codePoints().forEach(cp -> b.appendCodePoint(cp + offset));
                    formattedInteger = b.toString();
                }
                if (format.regular) {
                    GroupingSeparator sep = format.groupingSeparators.get(0);
                    int n = (int) Math.floor((formattedInteger.length() - 1) / (double) sep.position);
                    for (int i = n; i > 0; i--) {
                        int pos = formattedInteger.length() - i * sep.position;
                        formattedInteger = jsSubstr(formattedInteger, 0, pos) + sep.character + jsSubstr(formattedInteger, pos, null);
                    }
                } else {
                    for (int i = format.groupingSeparators.size() - 1; i >= 0; i--) {
                        GroupingSeparator separator = format.groupingSeparators.get(i);
                        int pos = formattedInteger.length() - separator.position;
                        formattedInteger = jsSubstr(formattedInteger, 0, pos) + separator.character + jsSubstr(formattedInteger, pos, null);
                    }
                }

                if (format.ordinal) {
                    char lastDigit = formattedInteger.charAt(formattedInteger.length() - 1);
                    String suffix = lastDigit == '1' ? "st" : lastDigit == '2' ? "nd" : lastDigit == '3' ? "rd" : null;
                    if (suffix == null || (formattedInteger.length() > 1 && formattedInteger.charAt(formattedInteger.length() - 2) == '1')) {
                        suffix = "th";
                    }
                    formattedInteger += suffix;
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

    private static int[] decimalGroups = {
        0x30, 0x0660, 0x06F0, 0x07C0, 0x0966, 0x09E6, 0x0A66, 0x0AE6, 0x0B66, 0x0BE6, 0x0C66, 0x0CE6, 0x0D66, 0x0DE6, 0x0E50, 0x0ED0, 0x0F20, 0x1040, 0x1090, 0x17E0, 0x1810,
        0x1946, 0x19D0, 0x1A80, 0x1A90, 0x1B50, 0x1BB0, 0x1C40, 0x1C50, 0xA620, 0xA8D0, 0xA900, 0xA9D0, 0xA9F0, 0xAA50, 0xABF0, 0xFF10
    };

    private static Format analyseIntegerPicture(String picture) {
        Format format = new Format();
        String primaryFormat, formatModifier;
        int semicolon = picture.lastIndexOf(";");
        if (semicolon == -1) {
            primaryFormat = picture;
        } else {
            primaryFormat = picture.substring(0, semicolon);
            formatModifier = picture.substring(semicolon + 1);
            if (formatModifier.length() > 0 && formatModifier.charAt(0) == 'o') {
                format.ordinal = true;
            }
        }

        switch (primaryFormat) {
            case "A":
                format.case_type = tcase.UPPER;
            case "a":
                format.primary = formats.LETTERS;
                break;
            case "I":
                format.case_type = tcase.UPPER;
            case "i":
                format.primary = formats.ROMAN;
                break;
            case "W":
                format.case_type = tcase.UPPER;
                format.primary = formats.WORDS;
                break;
            case "Ww":
                format.case_type = tcase.TITLE;
                format.primary = formats.WORDS;
                break;
            case "w":
                format.primary = formats.WORDS;
                break;
            default: {
                Integer zeroCode = null;
                int mandatoryDigits = 0;
                int optionalDigits = 0;
                List<GroupingSeparator> groupingSeparators = new ArrayList<>();
                int separatorPosition = 0;
                int[] formatCodepoints = primaryFormat.codePoints().toArray();
                for (int ix = formatCodepoints.length - 1; ix >= 0; ix--) {
                    int codePoint = formatCodepoints[ix];
                    boolean digit = false;
                    for (int i = 0; i < decimalGroups.length; i++) {
                        int group = decimalGroups[i];
                        if (codePoint >= group && codePoint <= group + 9) {
                            digit = true;
                            mandatoryDigits++;
                            separatorPosition++;
                            if (zeroCode == null) {
                                zeroCode = group;
                            } else if (group != zeroCode) {
                                throw new JException("D3131", -1);
                            }
                            break;
                        }
                    }
                    if (!digit) {
                        if (codePoint == 0x23) {
                            separatorPosition++;
                            optionalDigits++;
                        } else {
                            groupingSeparators.add(new GroupingSeparator(separatorPosition, new String(Character.toChars(codePoint))));
                        }
                    }
                }
                if (mandatoryDigits > 0) {
                    format.primary = formats.DECIMAL;
                    format.zeroCode = zeroCode;
                    format.mandatoryDigits = mandatoryDigits;
                    format.optionalDigits = optionalDigits;

                    int regular = getRegularRepeat(groupingSeparators);
                    if (regular > 0) {
                        format.regular = true;
                        format.groupingSeparators.add(new GroupingSeparator(regular, groupingSeparators.get(0).character));
                    } else {
                        format.regular = false;
                        format.groupingSeparators = groupingSeparators;
                    }
                } else {
                    format.primary = formats.SEQUENCE;
                    format.token = primaryFormat;
                }
            }

        }

        return format;
    }

    private static int getRegularRepeat(List<GroupingSeparator> separators) {
        if (separators.size() == 0) {
            return 0;
        }

        String sepChar = separators.get(0).character;
        for (int i = 1; i < separators.size(); i++) {
            if (!separators.get(i).character.equals(sepChar)) {
                return 0;
            }
        }

        List<Integer> indexes = separators.stream().map(seperator -> seperator.position).collect(Collectors.toList());
        int factor = indexes.stream()
            .reduce((a, b) -> BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).intValue())
            .get();
        for (int index = 1; index <= indexes.size(); index++) {
            if (indexes.indexOf(index * factor) == -1) {
                return 0;
            }
        }
        return factor;
    }

    private static Map<Character, String> defaultPresentationModifiers = createDefaultPresentationModifiers();

    private static Map<Character, String> createDefaultPresentationModifiers() {
        Map<Character, String> map = new HashMap<>();
        map.put('Y', "1");
        map.put('M', "1");
        map.put('D', "1");
        map.put('d', "1");
        map.put('F', "n");
        map.put('W', "1");
        map.put('w', "1");
        map.put('X', "1");
        map.put('x', "1");
        map.put('H', "1");
        map.put('h', "1");
        map.put('P', "n");
        map.put('m', "01");
        map.put('s', "01");
        map.put('f', "1");
        map.put('Z', "01:01");
        map.put('z', "01:01");
        map.put('C', "n");
        map.put('E', "n");
        return map;
    }

    private static class PictureFormat {

        @SuppressWarnings("unused")
        String type;
        List<SpecPart> parts = new ArrayList<>();

        public PictureFormat(String type) {
            this.type = type;
        }

        public void addLiteral(String picture, int start, int end) {
            if (end > start) {
                String literal = picture.substring(start, end);
                // replace any doubled ]] with single ]
                literal = literal.replace("]]", "]");
                parts.add(new SpecPart("literal", literal));
            }
        }
    }

    private static class SpecPart {

        String type;
        String value;
        /** the component specifier, '\0' if the marker is empty */
        char component;
        /** a width modifier is present */
        boolean width;
        /** min / max width as JavaScript numbers, null = undefined */
        Double widthMin, widthMax;
        String presentation1;
        Character presentation2;
        boolean ordinal = false;
        public tcase names;
        public Format integerFormat;
        public double n = -1;

        public SpecPart(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public SpecPart(String type, char component) {
            this.type = type;
            this.component = component;
        }

        boolean isComponent(String list) {
            return component != '\0' && list.indexOf(component) != -1;
        }
    }

    private static final Pattern WHITESPACE = Pattern.compile("[" + JsRegex.WS + "]+");

    private static PictureFormat analyseDateTimePicture(String picture) {
        PictureFormat format = new PictureFormat("datetime");
        int start = 0, pos = 0;
        while (pos < picture.length()) {
            if (picture.charAt(pos) == '[') {
                //check it's not a doubled [[
                if (pos + 1 < picture.length() && picture.charAt(pos + 1) == '[') {
                    //literal [
                    format.addLiteral(picture, start, pos);
                    format.parts.add(new SpecPart("literal", "["));
                    pos += 2;
                    start = pos;
                    continue;
                }
                format.addLiteral(picture, start, pos);
                start = pos;
                pos = picture.indexOf("]", start);
                if (pos == -1) {
                    throw new JException("D3135", -1);
                }
                String marker = picture.substring(start + 1, pos);
                // whitespace within a variable marker is ignored (i.e. remove it)
                marker = WHITESPACE.matcher(marker).replaceAll("");
                SpecPart def = new SpecPart("marker", marker.isEmpty() ? '\0' : marker.charAt(0));
                int comma = marker.lastIndexOf(",");
                String presMod;
                if (comma != -1) {
                    String widthMod = marker.substring(comma + 1);
                    int dash = widthMod.indexOf("-");
                    String min, max = null;
                    if (dash == -1) {
                        min = widthMod;
                    } else {
                        min = widthMod.substring(0, dash);
                        max = widthMod.substring(dash + 1);
                    }
                    def.width = true;
                    def.widthMin = parseWidth(min);
                    def.widthMax = parseWidth(max);
                    presMod = marker.substring(1, comma);
                } else {
                    presMod = marker.length() > 0 ? marker.substring(1) : "";
                }
                if (presMod.length() == 1) {
                    def.presentation1 = presMod;
                } else if (presMod.length() > 1) {
                    char lastChar = presMod.charAt(presMod.length() - 1);
                    if ("atco".indexOf(lastChar) != -1) {
                        def.presentation2 = lastChar;
                        if (lastChar == 'o') {
                            def.ordinal = true;
                        }
                        def.presentation1 = presMod.substring(0, presMod.length() - 1);
                    } else {
                        def.presentation1 = presMod;
                    }
                } else {
                    def.presentation1 = def.component == '\0' ? null : defaultPresentationModifiers.get(def.component);
                }
                if (def.presentation1 == null) {
                    // unknown component specifier
                    throw new JException("D3132", -1, def.component == '\0' ? "" : String.valueOf(def.component));
                }
                if (def.presentation1.charAt(0) == 'n') {
                    def.names = tcase.LOWER;
                } else if (def.presentation1.charAt(0) == 'N') {
                    if (def.presentation1.length() > 1 && def.presentation1.charAt(1) == 'n') {
                        def.names = tcase.TITLE;
                    } else {
                        def.names = tcase.UPPER;
                    }
                } else if (def.isComponent("YMDdFWwXxHhmsf")) {
                    String integerPattern = def.presentation1;
                    if (def.presentation2 != null) {
                        integerPattern += ";" + def.presentation2;
                    }
                    def.integerFormat = analyseIntegerPicture(integerPattern);
                    if (def.width && def.widthMin != null) {
                        if (def.integerFormat.mandatoryDigits < def.widthMin) {
                            def.integerFormat.mandatoryDigits = def.widthMin;
                        }
                    }
                    if (def.component == 'Y') {
                        // §9.8.4.4
                        def.n = -1;
                        if (def.width && def.widthMax != null) {
                            def.n = def.widthMax;
                            def.integerFormat.mandatoryDigits = def.n;
                        } else {
                            double w = def.integerFormat.mandatoryDigits + def.integerFormat.optionalDigits;
                            if (w >= 2) {
                                def.n = w;
                            }
                        }
                    }
                    // if the previous part is also an integer with no intervening markup, then its width for parsing must be precisely defined
                    if (!format.parts.isEmpty()) {
                        SpecPart previousPart = format.parts.get(format.parts.size() - 1);
                        if (previousPart.integerFormat != null) {
                            previousPart.integerFormat.parseWidth = previousPart.integerFormat.mandatoryDigits;
                        }
                    }
                }
                if (def.component == 'Z' || def.component == 'z') {
                    def.integerFormat = analyseIntegerPicture(def.presentation1);
                }
                format.parts.add(def);
                start = pos + 1;
            }
            pos++;
        }
        format.addLiteral(picture, start, pos);
        return format;
    }

    private static Double parseWidth(String wm) {
        if (wm == null || wm.equals("*")) {
            return null;
        } else {
            return jsParseInt(wm);
        }
    }

    private static String[] days = {
        "", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };
    private static String[] months = {
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"
    };

    //
    // JavaScript Date based components (port of jsonata-js getDateTimeFragment)
    //

    /** start of the first week of the given year / month (0-based) in millis, ISO 8601 / XPath F&amp;O */
    private static double startOfFirstWeek(double year, double month) {
        double jan1 = JsDate.utc(year, month);
        double dayOfJan1 = JsDate.getUTCDay(jan1);
        if (dayOfJan1 == 0) {
            dayOfJan1 = 7;
        }
        // if Jan 1 is Fri, Sat or Sun, then add the number of days (in millis) to jan1 to get the start of week 1
        return dayOfJan1 > 4 ? jan1 + (8 - dayOfJan1) * JsDate.MS_PER_DAY : jan1 - (dayOfJan1 - 1) * JsDate.MS_PER_DAY;
    }

    private static double deltaWeeks(double start, double end) {
        return (end - start) / (JsDate.MS_PER_DAY * 7) + 1;
    }

    /** yearMonth(year, month).nextMonth() as {year, month} */
    private static double[] nextMonth(double year, double month) {
        return month == 11 ? new double[] {year + 1, 0} : new double[] {year, month + 1};
    }

    private static double[] previousMonth(double year, double month) {
        return month == 0 ? new double[] {year - 1, 11} : new double[] {year, month - 1};
    }

    /**
     * @param date a JavaScript time value (NaN = invalid date)
     * @return Double, String or null (undefined)
     */
    private static Object getDateTimeFragment(double date, char component) {
        switch (component) {
            case 'Y': // year
                return JsDate.getUTCFullYear(date);
            case 'M': // month in year
                return JsDate.getUTCMonth(date) + 1;
            case 'D': // day in month
                return JsDate.getUTCDate(date);
            case 'd': { // day in year
                double today = JsDate.utc(JsDate.getUTCFullYear(date), JsDate.getUTCMonth(date), JsDate.getUTCDate(date), 0, 0, 0, 0);
                double firstJan = JsDate.utc(JsDate.getUTCFullYear(date), 0);
                return (today - firstJan) / JsDate.MS_PER_DAY + 1;
            }
            case 'F': { // day of week
                double v = JsDate.getUTCDay(date);
                if (v == 0) {
                    // ISO 8601 defines days 1-7: Mon-Sun
                    v = 7;
                }
                return v;
            }
            case 'W': { // week in year
                double year = JsDate.getUTCFullYear(date);
                double startOfWeek1 = startOfFirstWeek(year, 0);
                double today = JsDate.utc(year, JsDate.getUTCMonth(date), JsDate.getUTCDate(date), 0, 0, 0, 0);
                double week = deltaWeeks(startOfWeek1, today);
                if (week > 52) {
                    // might be first week of the following year
                    double startOfFollowingYear = startOfFirstWeek(year + 1, 0);
                    if (today >= startOfFollowingYear) {
                        week = 1;
                    }
                } else if (week < 1) {
                    // must be end of the previous year
                    double startOfPreviousYear = startOfFirstWeek(year - 1, 0);
                    week = deltaWeeks(startOfPreviousYear, today);
                }
                return Math.floor(week);
            }
            case 'w': { // week in month
                double year = JsDate.getUTCFullYear(date);
                double month = JsDate.getUTCMonth(date);
                double startOfWeek1 = startOfFirstWeek(year, month);
                double today = JsDate.utc(year, month, JsDate.getUTCDate(date), 0, 0, 0, 0);
                double week = deltaWeeks(startOfWeek1, today);
                if (week > 4) {
                    // might be first week of the following month
                    double[] next = nextMonth(year, month);
                    double startOfFollowingMonth = startOfFirstWeek(next[0], next[1]);
                    if (today >= startOfFollowingMonth) {
                        week = 1;
                    }
                } else if (week < 1) {
                    // must be end of the previous month
                    double[] prev = previousMonth(year, month);
                    double startOfPreviousMonth = startOfFirstWeek(prev[0], prev[1]);
                    week = deltaWeeks(startOfPreviousMonth, today);
                }
                return Math.floor(week);
            }
            case 'X': { // ISO week-numbering year
                double year = JsDate.getUTCFullYear(date);
                double startOfISOYear = startOfFirstWeek(year, 0);
                double endOfISOYear = startOfFirstWeek(year + 1, 0);
                if (date < startOfISOYear) {
                    return year - 1;
                } else if (date >= endOfISOYear) {
                    return year + 1;
                } else {
                    return year;
                }
            }
            case 'x': { // ISO week-numbering month
                double year = JsDate.getUTCFullYear(date);
                double month = JsDate.getUTCMonth(date);
                double startOfISOMonth = startOfFirstWeek(year, month);
                double[] next = nextMonth(year, month);
                double endOfISOMonth = startOfFirstWeek(next[0], next[1]);
                if (date < startOfISOMonth) {
                    return previousMonth(year, month)[1] + 1;
                } else if (date >= endOfISOMonth) {
                    return next[1] + 1;
                } else {
                    return month + 1;
                }
            }
            case 'H': // hour in day (24 hours)
                return JsDate.getUTCHours(date);
            case 'h': { // hour in half-day (12 hours)
                double v = JsDate.getUTCHours(date) % 12;
                if (v == 0) {
                    v = 12;
                }
                return v;
            }
            case 'P': // am/pm marker
                return JsDate.getUTCHours(date) >= 12 ? "pm" : "am";
            case 'm': // minute in hour
                return JsDate.getUTCMinutes(date);
            case 's': // second in minute
                return JsDate.getUTCSeconds(date);
            case 'f': // fractional seconds
                return JsDate.getUTCMilliseconds(date);
            case 'C': // calendar name
            case 'E': // era
                return "ISO";
            default:
                // Z, z: since the date object is constructed from epoch millis, the TZ component is always be UTC.
                return null;
        }
    }

    //
    // parsing
    //

    private static final class MatcherPart {

        String regex;
        char component;
        /** null for literals; the function may return null (undefined) */
        Function<String, Double> parse;

        MatcherPart(String regex, Function<String, Double> parse) {
            this.regex = regex;
            this.parse = parse;
        }
    }

    /** JavaScript order of the keys of the wordValues object of jsonata-js */
    private static final Map<String, Double> wordValuesJs = new LinkedHashMap<>();
    static {
        for (int i = 0; i < few.length; i++)
            wordValuesJs.put(few[i].toLowerCase(), (double) i);
        for (int i = 0; i < ordinals.length; i++)
            wordValuesJs.put(ordinals[i].toLowerCase(), (double) i);
        for (int i = 0; i < decades.length; i++) {
            String lword = decades[i].toLowerCase();
            wordValuesJs.put(lword, (i + 2) * 10d);
            wordValuesJs.put(lword.substring(0, decades[i].length() - 1) + "ieth", (i + 2) * 10d);
        }
        wordValuesJs.put("hundredth", 100d);
        for (int i = 0; i < magnitudes.length; i++) {
            String lword = magnitudes[i].toLowerCase();
            double val = Math.pow(10, (i + 1) * 3);
            wordValuesJs.put(lword, val);
            wordValuesJs.put(lword + "th", val);
        }
    }

    private static final Pattern WORD_SEPARATORS = JsRegex.compile(",\\s|\\sand\\s|[\\s\\\\-]", false, false);

    /** jsonata-js wordsToNumber */
    private static double wordsToNumberJs(String text) {
        String[] parts = WORD_SEPARATORS.split(text, -1);
        List<Double> segs = new ArrayList<>();
        segs.add(0d);
        for (String part : parts) {
            Double value = wordValuesJs.get(part);
            if (value != null && value < 100) {
                double top = segs.remove(segs.size() - 1);
                if (top >= 1000) {
                    segs.add(top);
                    top = 0;
                }
                segs.add(top + value);
            } else {
                double top = segs.remove(segs.size() - 1);
                segs.add(top * (value == null ? Double.NaN : value));
            }
        }
        double result = 0;
        for (double d : segs)
            result += d;
        return result;
    }

    private static final Map<Character, Double> romanValuesJs = Map.of('M', 1000d, 'D', 500d, 'C', 100d, 'L', 50d, 'X', 10d, 'V', 5d, 'I', 1d);

    /** jsonata-js romanToDecimal */
    private static double romanToDecimalJs(String roman) {
        double decimal = 0;
        double max = 1;
        for (int i = roman.length() - 1; i >= 0; i--) {
            Double value = romanValuesJs.get(roman.charAt(i));
            if (value == null)
                return Double.NaN;
            if (value < max) {
                decimal -= value;
            } else {
                max = value;
                decimal += value;
            }
        }
        return decimal;
    }

    /** jsonata-js lettersToDecimal */
    private static double lettersToDecimalJs(String letters, char aChar) {
        double decimal = 0;
        for (int i = 0; i < letters.length(); i++) {
            decimal += (letters.charAt(letters.length() - i - 1) - aChar + 1) * Math.pow(26, i);
        }
        return decimal;
    }

    private static List<MatcherPart> generateRegex(PictureFormat formatSpec) {
        List<MatcherPart> parts = new ArrayList<>();
        for (final SpecPart part : formatSpec.parts) {
            MatcherPart res;
            if (part.type.equals("literal")) {
                res = new MatcherPart(part.value.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0"), null);
            } else if (part.component == 'Z' || part.component == 'z') {
                // timezone
                final String separator = part.integerFormat.regular ? part.integerFormat.groupingSeparators.get(0).character : null;
                String regex = "";
                if (part.component == 'z') {
                    regex = "GMT";
                }
                regex += "[-+][0-9]+";
                if (separator != null) {
                    regex += separator + "[0-9]+";
                }
                res = new MatcherPart(regex, value -> {
                    if (part.component == 'z') {
                        value = value.substring(3); // remove the leading GMT
                    }
                    double offsetHours = 0, offsetMinutes = 0;
                    if (separator != null) {
                        int idx = value.indexOf(separator);
                        offsetHours = jsParseInt(jsSubstring(value, 0, idx));
                        offsetMinutes = jsParseInt(jsSubstring(value, idx + 1, value.length()));
                    } else {
                        // depends on number of digits
                        int numdigits = value.length() - 1;
                        if (numdigits <= 2) {
                            // just hour offset
                            offsetHours = jsParseInt(value);
                        } else {
                            offsetHours = jsParseInt(value.substring(0, 3));
                            offsetMinutes = jsParseInt(value.substring(3));
                        }
                    }
                    // unlike jsonata-js, the minutes of a negative offset are negative as well (-05:30 = -330)
                    if (value.startsWith("-"))
                        offsetMinutes = -offsetMinutes;
                    return offsetHours * 60 + offsetMinutes;
                });
            } else if (part.component == 'f') {
                res = new MatcherPart("[0-9]+", value -> Double.parseDouble("0." + value.substring(0, Math.min(3, value.length()))) * 1000);
            } else if (part.integerFormat != null) {
                res = generateRegex(part.integerFormat);
            } else {
                // must be a month or day name
                final Map<String, Double> lookup = new HashMap<>();
                if (part.component == 'M' || part.component == 'x') {
                    // months
                    for (int i = 0; i < months.length; i++) {
                        if (part.width && truthy(part.widthMax)) {
                            lookup.put(jsSubstring(months[i], 0, part.widthMax), i + 1d);
                        } else {
                            lookup.put(months[i], i + 1d);
                        }
                    }
                } else if (part.component == 'F') {
                    // days
                    for (int i = 1; i < days.length; i++) {
                        if (part.width && truthy(part.widthMax)) {
                            lookup.put(jsSubstring(days[i], 0, part.widthMax), (double) i);
                        } else {
                            lookup.put(days[i], (double) i);
                        }
                    }
                } else if (part.component == 'P') {
                    lookup.put("am", 0d);
                    lookup.put("AM", 0d);
                    lookup.put("pm", 1d);
                    lookup.put("PM", 1d);
                } else {
                    // unsupported 'name' option for this component
                    throw new JException("D3133", -1, String.valueOf(part.component));
                }
                res = new MatcherPart("[a-zA-Z]+", value -> lookup.get(value));
            }
            res.component = part.component;
            parts.add(res);
        }
        return parts;
    }

    private static MatcherPart generateRegex(final Format formatSpec) {
        final boolean isUpper = formatSpec.case_type == tcase.UPPER;
        switch (formatSpec.primary) {
            case LETTERS:
                return new MatcherPart(isUpper ? "[A-Z]+" : "[a-z]+", value -> lettersToDecimalJs(value, isUpper ? 'A' : 'a'));
            case ROMAN:
                return new MatcherPart(isUpper ? "[MDCLXVI]+" : "[mdclxvi]+", value -> romanToDecimalJs(isUpper ? value : value.toUpperCase()));
            case WORDS: {
                List<String> alternatives = new ArrayList<>(wordValuesJs.keySet());
                alternatives.add("and");
                alternatives.add("[\\-, ]");
                return new MatcherPart("(?:" + String.join("|", alternatives) + ")+", value -> wordsToNumberJs(value.toLowerCase()));
            }
            case DECIMAL: {
                String regex = "[0-9]";
                if (truthy(formatSpec.parseWidth)) {
                    regex += "{" + Utils.jsNumberToString(formatSpec.parseWidth) + "}";
                } else {
                    regex += "+";
                }
                if (formatSpec.ordinal) {
                    // ordinals
                    regex += "(?:th|st|nd|rd)";
                }
                return new MatcherPart(regex, value -> {
                    String digits = value;
                    if (formatSpec.ordinal) {
                        // strip off the suffix
                        digits = value.substring(0, value.length() - 2);
                    }
                    // strip out the separators
                    if (formatSpec.regular) {
                        digits = digits.replace(",", "");
                    } else {
                        for (GroupingSeparator sep : formatSpec.groupingSeparators) {
                            digits = digits.replace(sep.character, "");
                        }
                    }
                    if (formatSpec.zeroCode != 0x30) {
                        // apply offset
                        StringBuilder b = new StringBuilder();
                        for (int i = 0; i < digits.length(); i++) {
                            int cp = digits.charAt(i) - formatSpec.zeroCode + 0x30;
                            if (cp < 0 || cp > 0x10FFFF)
                                throw jsError("Invalid code point " + cp);
                            b.appendCodePoint(cp);
                        }
                        digits = b.toString();
                    }
                    return jsParseInt(digits);
                });
            }
            case SEQUENCE:
            default:
                throw new JException("D3130", -1, formatSpec.token);
        }
    }

    /**
     * parse a string containing a timestamp as specified by the picture string (port of jsonata-js parseDateTime)
     *
     * @param now the timestamp of the evaluation (defaults for unspecified components)
     * @return the millis since the epoch (Long, Double.NaN for an invalid date) or null if the timestamp does not
     *         match the picture
     */
    public static Number parseDateTime(String timestamp, String picture, long now) {
        PictureFormat formatSpec = analyseDateTimePicture(picture);
        List<MatcherPart> matchSpec = generateRegex(formatSpec);
        StringBuilder fullRegex = new StringBuilder("^");
        for (MatcherPart part : matchSpec) {
            fullRegex.append('(').append(part.regex).append(')');
        }
        fullRegex.append('$');
        Pattern pattern = JsRegex.compile(fullRegex.toString(), true, false);
        Matcher info = pattern.matcher(timestamp);
        if (!info.find())
            return null;

        //    date mask             YXMxWwdD
        final int dmA = 161; // binary 10100001
        final int dmB = 130; // binary 10000010
        final int dmC = 84; // binary 01010100
        final int dmD = 72; // binary 01001000
        //    time mask             PHhmsf
        final int tmA = 23; // binary 010111
        final int tmB = 47; // binary 101111

        // value null = property present, but undefined
        Map<Character, Double> components = new HashMap<>();
        for (int i = 1; i <= info.groupCount() && i <= matchSpec.size(); i++) {
            MatcherPart mpart = matchSpec.get(i - 1);
            if (mpart.parse != null) {
                components.put(mpart.component, mpart.parse.apply(info.group(i)));
            }
        }

        if (components.isEmpty()) {
            // nothing specified
            return null;
        }

        int mask = 0;
        for (char part : "YXMxWwdD".toCharArray()) {
            mask <<= 1;
            mask += truthy(components.get(part)) ? 1 : 0;
        }
        boolean dateA = isType(dmA, mask);
        boolean dateB = !dateA && isType(dmB, mask);
        boolean dateC = isType(dmC, mask);
        boolean dateD = !dateC && isType(dmD, mask);

        mask = 0;
        for (char part : "PHhmsf".toCharArray()) {
            mask <<= 1;
            mask += truthy(components.get(part)) ? 1 : 0;
        }
        boolean timeA = isType(tmA, mask);
        boolean timeB = !timeA && isType(tmB, mask);

        // should only be zero or one date type and zero or one time type
        String dateComps = dateB ? "YD" : dateC ? "XxwF" : dateD ? "XWF" : "YMD";
        String timeComps = timeB ? "Phmsf" : "Hmsf";
        String comps = dateComps + timeComps;

        // step through the candidate parts from most significant to least significant
        // default the most significant unspecified parts to current timestamp component
        // default the least significant unspecified parts to zero
        // if any gaps in between the specified parts, throw an error
        boolean startSpecified = false;
        boolean endSpecified = false;
        for (char part : comps.toCharArray()) {
            if (components.get(part) == null) {
                if (startSpecified) {
                    // past the specified block - default to zero
                    components.put(part, "MDd".indexOf(part) != -1 ? 1d : 0d);
                    endSpecified = true;
                } else {
                    // haven't hit the specified block yet, default to current timestamp
                    Object v = getDateTimeFragment(now, part);
                    components.put(part, v instanceof Double ? (Double) v : Double.NaN);
                }
            } else {
                startSpecified = true;
                if (endSpecified) {
                    throw new JException("D3136", -1);
                }
            }
        }

        // validate and fill in components
        Double month = components.get('M');
        if (month != null && month > 0) {
            components.put('M', month - 1); // Date.UTC requires a zero-indexed month
        } else {
            components.put('M', 0d); // default to January
        }
        if (dateB) {
            // millis for given 1st Jan of that year (at 00:00 UTC)
            double firstJan = JsDate.utc(num(components.get('Y')), 0);
            double offsetMillis = (num(components.get('d')) - 1) * JsDate.MS_PER_DAY;
            double derivedDate = JsDate.timeClip(firstJan + offsetMillis);
            components.put('M', JsDate.getUTCMonth(derivedDate));
            components.put('D', JsDate.getUTCDate(derivedDate));
        }
        if (dateC) {
            // parsing this format not currently supported
            throw new JException("D3136", -1);
        }
        if (dateD) {
            // parsing this format (ISO week date) not currently supported
            throw new JException("D3136", -1);
        }
        if (timeB) {
            // 12hr to 24hr
            double h = num(components.get('h'));
            double hour = h == 12 ? 0 : h;
            Double p = components.get('P');
            if (p != null && p == 1) {
                hour += 12;
            }
            components.put('H', hour);
        }

        double millis = JsDate.utc(num(components.get('Y')), num(components.get('M')), num(components.get('D')),
            num(components.get('H')), num(components.get('m')), num(components.get('s')), num(components.get('f')));
        Double tz = truthy(components.get('Z')) ? components.get('Z') : components.get('z');
        if (truthy(tz)) {
            // adjust for timezone
            millis -= tz * 60 * 1000;
        }
        if (Double.isNaN(millis))
            return Double.NaN;
        if (millis == Math.rint(millis) && Math.abs(millis) < 9.007199254740992E15)
            return (long) millis;
        return millis;
    }

    /** ToNumber of a component (undefined = NaN) */
    private static double num(Double d) {
        return d == null ? Double.NaN : d;
    }

    private static boolean isType(int type, int mask) {
        // shouldn't match any 0's, must match at least one 1
        return ((~type & mask) == 0) && (type & mask) != 0;
    }

    //
    // formatting
    //

    private static PictureFormat iso8601Spec = null;

    public static String formatDateTime(long millis, String picture, String timezone) {
        int offsetHours = 0;
        int offsetMinutes = 0;

        if (timezone != null) {
            int offset = Integer.parseInt(timezone);
            offsetHours = offset / 100;
            offsetMinutes = offset % 100;
        }
        PictureFormat formatSpec;
        if (picture == null) {
            if (iso8601Spec == null) {
                iso8601Spec = analyseDateTimePicture("[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001][Z01:01t]");
            }
            formatSpec = iso8601Spec;
        } else {
            formatSpec = analyseDateTimePicture(picture);
        }

        int offsetMillis = (60 * offsetHours + offsetMinutes) * 60 * 1000;
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis + offsetMillis), ZoneOffset.UTC);
        String result = "";
        for (SpecPart part : formatSpec.parts) {
            if (part.type.equals("literal")) {
                result += part.value;
            } else {
                result += formatComponent(dateTime, part, offsetHours, offsetMinutes);
            }
        }

        return result;
    }

    private static String formatComponent(LocalDateTime date, SpecPart markerSpec, int offsetHours, int offsetMinutes) {
        String componentValue = getDateTimeFragment(date, markerSpec.component);

        if (markerSpec.isComponent("YMDdFWwXxHhms")) {
            if (markerSpec.component == 'Y') {
                if (markerSpec.n != -1) {
                    componentValue = "" + (int) (Integer.parseInt(componentValue) % Math.pow(10, markerSpec.n));
                }
            }
            if (markerSpec.names != null) {
                if (markerSpec.component == 'M' || markerSpec.component == 'x') {
                    componentValue = months[Integer.parseInt(componentValue) - 1];
                } else if (markerSpec.component == 'F') {
                    componentValue = days[Integer.parseInt(componentValue)];
                } else {
                    throw new RuntimeException(String.format(Constants.ERR_MSG_INVALID_NAME_MODIFIER, markerSpec.component));
                }
                if (markerSpec.names == tcase.UPPER) {
                    componentValue = componentValue.toUpperCase();
                } else if (markerSpec.names == tcase.LOWER) {
                    componentValue = componentValue.toLowerCase();
                }
                if (markerSpec.widthMax != null && componentValue.length() > markerSpec.widthMax) {
                    componentValue = componentValue.substring(0, markerSpec.widthMax.intValue());
                }
            } else {
                componentValue = formatInteger(Integer.parseInt(componentValue), markerSpec.integerFormat);
            }
        } else if (markerSpec.component == 'f') {
            componentValue = formatInteger(Integer.parseInt(componentValue), markerSpec.integerFormat);
        } else if (markerSpec.component == 'Z' || markerSpec.component == 'z') {
            int offset = offsetHours * 100 + offsetMinutes;
            if (markerSpec.integerFormat.regular) {
                componentValue = formatInteger(offset, markerSpec.integerFormat);
            } else {
                double numDigits = markerSpec.integerFormat.mandatoryDigits;
                if (numDigits == 1 || numDigits == 2) {
                    componentValue = formatInteger(offsetHours, markerSpec.integerFormat);
                    if (offsetMinutes != 0) {
                        componentValue += ":" + formatInteger(offsetMinutes, "00");
                    }
                } else if (numDigits == 3 || numDigits == 4) {
                    componentValue = formatInteger(offset, markerSpec.integerFormat);
                } else {
                    throw new RuntimeException(Constants.ERR_MSG_TIMEZONE_FORMAT);
                }
            }
            if (offset >= 0) {
                componentValue = "+" + componentValue;
            }
            if (markerSpec.component == 'z') {
                componentValue = "GMT" + componentValue;
            }
            if (offset == 0 && markerSpec.presentation2 != null && markerSpec.presentation2 == 't') {
                componentValue = "Z";
            }
        } else if (markerSpec.component == 'P') {
            // §9.8.4.7 Formatting Other Components
            // Formatting P for am/pm
            // getDateTimeFragment() always returns am/pm lower case so check for UPPER here
            if (markerSpec.names == tcase.UPPER) {
                componentValue = componentValue.toUpperCase();
            }
        }
        return componentValue;
    }

    private static String getDateTimeFragment(LocalDateTime date, Character component) {
        String componentValue = "";
        switch (component) {
            case 'Y': // year
                componentValue = "" + date.getYear();
                break;
            case 'M': // month in year
                componentValue = "" + date.getMonthValue();
                break;
            case 'D': // day in month
                componentValue = "" + date.getDayOfMonth();
                break;
            case 'd': // day in year
                componentValue = "" + date.getDayOfYear();
                break;
            case 'F': // day of week
                componentValue = "" + date.getDayOfWeek().getValue();
                break;
            case 'W': // week in year
                componentValue = "" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                break;
            case 'w': // week in month
                componentValue = "" + date.get(WeekFields.ISO.weekOfMonth());
                break;
            case 'X':
                //TODO work these out once others verified
                componentValue = "" + date.getYear();
                break;
            case 'x':
                componentValue = "" + date.getMonthValue();
                break;
            case 'H': // hour in day (24 hours)
                componentValue = "" + date.getHour();
                break;
            case 'h': //hour in day (12 hours)
                int hour = date.getHour();
                if (hour > 12) {
                    hour -= 12;
                } else if (hour == 0) {
                    hour = 12;
                }
                componentValue = "" + hour;
                break;
            case 'P':
                componentValue = date.getHour() < 12 ? "am" : "pm";
                break;
            case 'm':
                componentValue = "" + date.getMinute();
                break;
            case 's':
                componentValue = "" + date.getSecond();
                break;
            case 'f':
                componentValue = "" + (date.getNano() / 1000000);
                break;
            case 'Z':
            case 'z':
                break;
            case 'C':
                componentValue = "ISO";
                break;
            case 'E':
                componentValue = "ISO";
                break;
        }
        return componentValue;
    }

    public static int lettersToDecimal(String letters, char aChar) {
        int decimal = 0;
        char[] chars = letters.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            decimal += (chars[chars.length - i - 1] - aChar + 1) * Math.pow(26, i);
        }
        return decimal;
    }
}
