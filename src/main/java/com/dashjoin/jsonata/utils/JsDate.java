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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * ECMAScript Date semantics on time values (doubles, NaN = invalid date), as used by
 * jsonata-js datetime.js: Date.UTC, the UTC getters, TimeClip and Date.parse (V8 algorithm).
 */
public final class JsDate {

    private JsDate() {
    }

    public static final double MS_PER_DAY = 86400000d;

    /** ToIntegerOrInfinity */
    static double toInteger(double d) {
        if (Double.isNaN(d))
            return 0;
        if (Double.isInfinite(d))
            return d;
        double r = d < 0 ? Math.ceil(d) : Math.floor(d);
        return r == 0 ? 0 : r;
    }

    /** TimeClip: NaN for non-finite or out of range values, otherwise the integral part */
    public static double timeClip(double t) {
        if (!Double.isFinite(t) || Math.abs(t) > 8.64e15)
            return Double.NaN;
        return toInteger(t);
    }

    /** days since the epoch of the first day of the given (proleptic Gregorian) year and month */
    private static double dayFromYearMonth(double year, double month) {
        // year is an integral double, month 0..11
        if (Math.abs(year) > 999999999)
            return year > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        return LocalDate.of((int) year, (int) month + 1, 1).toEpochDay();
    }

    /** MakeDay(year, month, date) */
    public static double makeDay(double year, double month, double date) {
        if (!Double.isFinite(year) || !Double.isFinite(month) || !Double.isFinite(date))
            return Double.NaN;
        double y = toInteger(year), m = toInteger(month), dt = toInteger(date);
        double ym = y + Math.floor(m / 12);
        double mn = m % 12;
        if (mn < 0)
            mn += 12;
        double day = dayFromYearMonth(ym, mn);
        if (!Double.isFinite(day))
            return Double.NaN;
        return day + dt - 1;
    }

    /** MakeTime(hour, min, sec, ms) */
    public static double makeTime(double hour, double min, double sec, double ms) {
        if (!Double.isFinite(hour) || !Double.isFinite(min) || !Double.isFinite(sec) || !Double.isFinite(ms))
            return Double.NaN;
        return toInteger(hour) * 3600000d + toInteger(min) * 60000d + toInteger(sec) * 1000d + toInteger(ms);
    }

    /** MakeDate(day, time) */
    public static double makeDate(double day, double time) {
        if (!Double.isFinite(day) || !Double.isFinite(time))
            return Double.NaN;
        double tv = day * MS_PER_DAY + time;
        return Double.isFinite(tv) ? tv : Double.NaN;
    }

    /**
     * Date.UTC(year, month, date, hours, minutes, seconds, ms) with all arguments given
     * (years 0..99 are mapped to 1900..1999 as in JavaScript)
     */
    public static double utc(double year, double month, double date, double hours, double minutes, double seconds, double ms) {
        if (!Double.isNaN(year)) {
            double yi = toInteger(year);
            if (yi >= 0 && yi <= 99)
                year = 1900 + yi;
        }
        return timeClip(makeDate(makeDay(year, month, date), makeTime(hours, minutes, seconds, ms)));
    }

    /** Date.UTC(year, month) */
    public static double utc(double year, double month) {
        return utc(year, month, 1, 0, 0, 0, 0);
    }

    private static LocalDateTime ldt(double t) {
        long ms = (long) t;
        long day = Math.floorDiv(ms, 86400000L);
        long rem = Math.floorMod(ms, 86400000L);
        return LocalDate.ofEpochDay(day).atStartOfDay().plusNanos(rem * 1000000L);
    }

    public static double getUTCFullYear(double t) {
        return Double.isNaN(t) ? Double.NaN : ldt(t).getYear();
    }

    /** 0-based month */
    public static double getUTCMonth(double t) {
        return Double.isNaN(t) ? Double.NaN : ldt(t).getMonthValue() - 1;
    }

    public static double getUTCDate(double t) {
        return Double.isNaN(t) ? Double.NaN : ldt(t).getDayOfMonth();
    }

    /** 0 = Sunday */
    public static double getUTCDay(double t) {
        if (Double.isNaN(t))
            return Double.NaN;
        return Math.floorMod(Math.floorDiv((long) t, 86400000L) + 4, 7L);
    }

    public static double getUTCHours(double t) {
        return Double.isNaN(t) ? Double.NaN : ldt(t).getHour();
    }

    public static double getUTCMinutes(double t) {
        return Double.isNaN(t) ? Double.NaN : ldt(t).getMinute();
    }

    public static double getUTCSeconds(double t) {
        return Double.isNaN(t) ? Double.NaN : ldt(t).getSecond();
    }

    public static double getUTCMilliseconds(double t) {
        return Double.isNaN(t) ? Double.NaN : Math.floorMod((long) t, 1000L);
    }

    //
    // Date.parse - port of the V8 date parser (src/date/dateparser*), restricted to the
    // characters the callers pass in: digits, '+', '-', ':', '.', 'T', 'Z' and others
    // that are ignored as in V8.
    //

    private static final int NONE = Integer.MAX_VALUE;

    /** ECMAScript WhiteSpace (V8 IsWhiteSpace) */
    private static final String WHITESPACE = "\t\u000B\f \u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000\ufeff";
    private static final String LINE_TERMINATORS = "\n\r\u2028\u2029";

    private static boolean between(int x, int lo, int hi) {
        return Integer.compareUnsigned(x - lo, hi - lo) <= 0;
    }

    private static boolean isMonth(int x) {
        return between(x, 1, 12);
    }

    private static boolean isDay(int x) {
        return between(x, 1, 31);
    }

    private static boolean isHour(int x) {
        return between(x, 0, 23);
    }

    private static boolean isMinute(int x) {
        return between(x, 0, 59);
    }

    private static boolean isSecond(int x) {
        return between(x, 0, 59);
    }

    private static boolean isHour12(int x) {
        return between(x, 0, 12);
    }

    private static boolean isMillisecond(int x) {
        return between(x, 0, 999);
    }

    private enum TokenType {
        INVALID, UNKNOWN, NUMBER, SYMBOL, WHITESPACE, KEYWORD, END
    }

    private enum KeywordType {
        INVALID, MONTH_NAME, TIME_ZONE_NAME, TIME_SEPARATOR, AM_PM
    }

    private static final class Token {
        TokenType tag;
        int value;
        int length;
        KeywordType keyword;

        Token(TokenType tag, int value, int length) {
            this.tag = tag;
            this.value = value;
            this.length = length;
        }

        boolean isNumber() {
            return tag == TokenType.NUMBER;
        }

        boolean isFixedLengthNumber(int len) {
            return isNumber() && length == len;
        }

        boolean isSymbol(char c) {
            return tag == TokenType.SYMBOL && value == c;
        }

        boolean isAsciiSign() {
            return tag == TokenType.SYMBOL && (value == '-' || value == '+');
        }

        int asciiSign() {
            return 44 - value;
        }

        boolean isEnd() {
            return tag == TokenType.END;
        }

        boolean isInvalid() {
            return tag == TokenType.INVALID;
        }

        boolean isWhiteSpace() {
            return tag == TokenType.WHITESPACE;
        }

        boolean isKeyword() {
            return tag == TokenType.KEYWORD;
        }

        boolean isKeywordType(KeywordType t) {
            return tag == TokenType.KEYWORD && keyword == t;
        }

        boolean isKeywordZ() {
            return isKeywordType(KeywordType.TIME_ZONE_NAME) && length == 1 && value == 0;
        }
    }

    private static final Object[][] KEYWORDS = {
        {"jan", KeywordType.MONTH_NAME, 1}, {"feb", KeywordType.MONTH_NAME, 2}, {"mar", KeywordType.MONTH_NAME, 3},
        {"apr", KeywordType.MONTH_NAME, 4}, {"may", KeywordType.MONTH_NAME, 5}, {"jun", KeywordType.MONTH_NAME, 6},
        {"jul", KeywordType.MONTH_NAME, 7}, {"aug", KeywordType.MONTH_NAME, 8}, {"sep", KeywordType.MONTH_NAME, 9},
        {"oct", KeywordType.MONTH_NAME, 10}, {"nov", KeywordType.MONTH_NAME, 11}, {"dec", KeywordType.MONTH_NAME, 12},
        {"am", KeywordType.AM_PM, 0}, {"pm", KeywordType.AM_PM, 12}, {"ut", KeywordType.TIME_ZONE_NAME, 0},
        {"utc", KeywordType.TIME_ZONE_NAME, 0}, {"z", KeywordType.TIME_ZONE_NAME, 0}, {"gmt", KeywordType.TIME_ZONE_NAME, 0},
        {"cdt", KeywordType.TIME_ZONE_NAME, -5}, {"cst", KeywordType.TIME_ZONE_NAME, -6}, {"edt", KeywordType.TIME_ZONE_NAME, -4},
        {"est", KeywordType.TIME_ZONE_NAME, -5}, {"mdt", KeywordType.TIME_ZONE_NAME, -6}, {"mst", KeywordType.TIME_ZONE_NAME, -7},
        {"pdt", KeywordType.TIME_ZONE_NAME, -7}, {"pst", KeywordType.TIME_ZONE_NAME, -8}, {"t", KeywordType.TIME_SEPARATOR, 0},
    };

    private static final class Scanner {
        final String s;
        int index = 0;
        int ch;
        Token next;

        Scanner(String s) {
            this.s = s;
            advance();
            next = scan();
        }

        void advance() {
            ch = index < s.length() ? s.charAt(index) : 0;
            index++;
        }

        boolean isDigit() {
            return ch >= '0' && ch <= '9';
        }

        boolean isWhiteSpaceChar() {
            return WHITESPACE.indexOf(ch) >= 0;
        }

        Token scan() {
            int pre = index;
            if (ch == 0)
                return new Token(TokenType.END, 0, 0);
            if (isDigit()) {
                int n = 0, i = 0;
                while (ch == '0')
                    advance();
                while (isDigit()) {
                    if (i < 9)
                        n = n * 10 + ch - '0';
                    i++;
                    advance();
                }
                return new Token(TokenType.NUMBER, n, index - pre);
            }
            if (ch == ':' || ch == '-' || ch == '+' || ch == '.' || ch == ')') {
                int c = ch;
                advance();
                return new Token(TokenType.SYMBOL, c, 1);
            }
            if (ch >= 'A' && !isWhiteSpaceChar()) {
                StringBuilder prefix = new StringBuilder();
                int len = 0;
                for (; ch >= 'A' && !isWhiteSpaceChar(); advance(), len++)
                    if (len < 3)
                        prefix.append(ch >= 'A' && ch <= 'Z' ? (char) (ch | 0x20) : (char) ch);
                String p = prefix.toString();
                for (Object[] k : KEYWORDS) {
                    String kw = (String) k[0];
                    if (p.equals(kw) && (len <= 3 || k[1] == KeywordType.MONTH_NAME)) {
                        Token t = new Token(TokenType.KEYWORD, (Integer) k[2], len);
                        t.keyword = (KeywordType) k[1];
                        return t;
                    }
                }
                Token t = new Token(TokenType.KEYWORD, 0, len);
                t.keyword = KeywordType.INVALID;
                return t;
            }
            if (WHITESPACE.indexOf(ch) >= 0 || LINE_TERMINATORS.indexOf(ch) >= 0) {
                advance();
                return new Token(TokenType.WHITESPACE, 0, index - pre);
            }
            if (ch == '(') {
                int balance = 0;
                do {
                    if (ch == ')')
                        --balance;
                    else if (ch == '(')
                        ++balance;
                    advance();
                } while (balance > 0 && ch != 0);
                return new Token(TokenType.UNKNOWN, 0, 0);
            }
            advance();
            return new Token(TokenType.UNKNOWN, 0, 0);
        }

        Token next() {
            Token t = next;
            next = scan();
            return t;
        }

        Token peek() {
            return next;
        }

        boolean skipSymbol(char c) {
            if (next.isSymbol(c)) {
                next();
                return true;
            }
            return false;
        }
    }

    private static final class DayComposer {
        int[] comp = new int[3];
        int index = 0;
        int namedMonth = NONE;
        boolean isoDate = false;

        boolean add(int n) {
            if (index < 3) {
                comp[index++] = n;
                return true;
            }
            return false;
        }

        boolean isEmpty() {
            return index == 0;
        }

        /** returns {year, month (0-based), day} or null */
        int[] write() {
            if (index < 1)
                return null;
            while (index < 3)
                comp[index++] = 1;
            int year = 0, month, day;
            if (namedMonth == NONE) {
                if (isoDate || (index == 3 && !isDay(comp[0]))) {
                    year = comp[0];
                    month = comp[1];
                    day = comp[2];
                } else {
                    month = comp[0];
                    day = comp[1];
                    if (index == 3)
                        year = comp[2];
                }
            } else {
                month = namedMonth;
                if (index == 1) {
                    day = comp[0];
                } else if (!isDay(comp[0])) {
                    year = comp[0];
                    day = comp[1];
                } else {
                    day = comp[0];
                    year = comp[1];
                }
            }
            if (!isoDate) {
                if (between(year, 0, 49))
                    year += 2000;
                else if (between(year, 50, 99))
                    year += 1900;
            }
            if (!isMonth(month) || !isDay(day))
                return null;
            return new int[] {year, month - 1, day};
        }
    }

    private static final class TimeComposer {
        int[] comp = new int[4];
        int index = 0;
        int hourOffset = NONE;

        boolean isEmpty() {
            return index == 0;
        }

        boolean isExpecting(int n) {
            return (index == 1 && isMinute(n)) || (index == 2 && isSecond(n)) || (index == 3 && isMillisecond(n));
        }

        boolean add(int n) {
            if (index < 4) {
                comp[index++] = n;
                return true;
            }
            return false;
        }

        boolean addFinal(int n) {
            if (!add(n))
                return false;
            while (index < 4)
                comp[index++] = 0;
            return true;
        }

        int[] write() {
            while (index < 4)
                comp[index++] = 0;
            int hour = comp[0], minute = comp[1], second = comp[2], ms = comp[3];
            if (hourOffset != NONE) {
                if (!isHour12(hour))
                    return null;
                hour %= 12;
                hour += hourOffset;
            }
            if (!isHour(hour) || !isMinute(minute) || !isSecond(second) || !isMillisecond(ms)) {
                if (hour != 24 || minute != 0 || second != 0 || ms != 0)
                    return null;
            }
            return new int[] {hour, minute, second, ms};
        }
    }

    private static final class TimeZoneComposer {
        int sign = NONE, hour = NONE, minute = NONE;

        void set(int offsetInHours) {
            sign = offsetInHours < 0 ? -1 : 1;
            hour = offsetInHours * sign;
            minute = 0;
        }

        void setSign(int s) {
            sign = s < 0 ? -1 : 1;
        }

        boolean isExpecting(int n) {
            return hour != NONE && minute == NONE && isMinute(n);
        }

        boolean isUTC() {
            return hour == 0 && minute == 0;
        }

        boolean isEmpty() {
            return hour == NONE;
        }

        /** offset in seconds, NaN for local time, null if invalid */
        Double write() {
            if (sign != NONE) {
                if (hour == NONE)
                    hour = 0;
                if (minute == NONE)
                    minute = 0;
                long total = (hour & 0xffffffffL) * 3600L + (minute & 0xffffffffL) * 60L;
                total &= 0xffffffffL;
                if (total > 0x3fffffffL)
                    return null;
                return (double) (sign < 0 ? -total : total);
            }
            return Double.NaN;
        }
    }

    private static int readMilliseconds(Token token) {
        int number = token.value;
        int length = token.length;
        if (length < 3) {
            if (length == 1)
                number *= 100;
            else if (length == 2)
                number *= 10;
        } else if (length > 3) {
            if (length > 9)
                length = 9;
            int factor = 1;
            do {
                factor *= 10;
                length--;
            } while (length > 3);
            number /= factor;
        }
        return number;
    }

    private static Token parseES5DateTime(Scanner scanner, DayComposer day, TimeComposer time, TimeZoneComposer tz) {
        if (scanner.peek().isAsciiSign()) {
            Token signToken = scanner.next();
            if (!scanner.peek().isFixedLengthNumber(6))
                return signToken;
            int sign = signToken.asciiSign();
            int year = scanner.next().value;
            if (sign < 0 && year == 0)
                return signToken;
            day.add(sign * year);
        } else if (scanner.peek().isFixedLengthNumber(4)) {
            day.add(scanner.next().value);
        } else {
            return scanner.next();
        }
        if (scanner.skipSymbol('-')) {
            if (!scanner.peek().isFixedLengthNumber(2) || !isMonth(scanner.peek().value))
                return scanner.next();
            day.add(scanner.next().value);
            if (scanner.skipSymbol('-')) {
                if (!scanner.peek().isFixedLengthNumber(2) || !isDay(scanner.peek().value))
                    return scanner.next();
                day.add(scanner.next().value);
            }
        }
        if (!scanner.peek().isKeywordType(KeywordType.TIME_SEPARATOR)) {
            if (!scanner.peek().isEnd())
                return scanner.next();
        } else {
            scanner.next();
            if (!scanner.peek().isFixedLengthNumber(2) || !between(scanner.peek().value, 0, 24))
                return new Token(TokenType.INVALID, 0, 0);
            boolean hourIs24 = scanner.peek().value == 24;
            time.add(scanner.next().value);
            if (!scanner.skipSymbol(':'))
                return new Token(TokenType.INVALID, 0, 0);
            if (!scanner.peek().isFixedLengthNumber(2) || !isMinute(scanner.peek().value)
                || (hourIs24 && scanner.peek().value > 0))
                return new Token(TokenType.INVALID, 0, 0);
            time.add(scanner.next().value);
            if (scanner.skipSymbol(':')) {
                if (!scanner.peek().isFixedLengthNumber(2) || !isSecond(scanner.peek().value)
                    || (hourIs24 && scanner.peek().value > 0))
                    return new Token(TokenType.INVALID, 0, 0);
                time.add(scanner.next().value);
                if (scanner.skipSymbol('.')) {
                    if (!scanner.peek().isNumber() || (hourIs24 && scanner.peek().value > 0))
                        return new Token(TokenType.INVALID, 0, 0);
                    time.add(readMilliseconds(scanner.next()));
                }
            }
            if (scanner.peek().isKeywordZ()) {
                scanner.next();
                tz.set(0);
            } else if (scanner.peek().isSymbol('+') || scanner.peek().isSymbol('-')) {
                tz.setSign(scanner.next().value == '+' ? 1 : -1);
                if (scanner.peek().isFixedLengthNumber(4)) {
                    int hourmin = scanner.next().value;
                    int hour = hourmin / 100;
                    int min = hourmin % 100;
                    if (!isHour(hour) || !isMinute(min))
                        return new Token(TokenType.INVALID, 0, 0);
                    tz.hour = hour;
                    tz.minute = min;
                } else {
                    if (!scanner.peek().isFixedLengthNumber(2) || !isHour(scanner.peek().value))
                        return new Token(TokenType.INVALID, 0, 0);
                    tz.hour = scanner.next().value;
                    if (!scanner.skipSymbol(':'))
                        return new Token(TokenType.INVALID, 0, 0);
                    if (!scanner.peek().isFixedLengthNumber(2) || !isMinute(scanner.peek().value))
                        return new Token(TokenType.INVALID, 0, 0);
                    tz.minute = scanner.next().value;
                }
            }
            if (!scanner.peek().isEnd())
                return new Token(TokenType.INVALID, 0, 0);
        }
        if (tz.isEmpty() && time.isEmpty())
            tz.set(0);
        day.isoDate = true;
        return new Token(TokenType.END, 0, 0);
    }

    /**
     * Date.parse(str) as implemented by V8 (Node.js)
     *
     * @return the time value or NaN
     */
    public static double parse(String str) {
        Scanner scanner = new Scanner(str);
        TimeZoneComposer tz = new TimeZoneComposer();
        TimeComposer time = new TimeComposer();
        DayComposer day = new DayComposer();

        Token token = parseES5DateTime(scanner, day, time, tz);
        if (token.isInvalid())
            return Double.NaN;
        boolean hasReadNumber = !day.isEmpty();
        for (; !token.isEnd(); token = scanner.next()) {
            if (token.isNumber()) {
                hasReadNumber = true;
                int n = token.value;
                if (scanner.skipSymbol(':')) {
                    if (scanner.skipSymbol(':')) {
                        if (!time.isEmpty())
                            return Double.NaN;
                        time.add(n);
                        time.add(0);
                    } else {
                        if (!time.add(n))
                            return Double.NaN;
                        if (scanner.peek().isSymbol('.'))
                            scanner.next();
                    }
                } else if (scanner.skipSymbol('.') && time.isExpecting(n)) {
                    time.add(n);
                    if (!scanner.peek().isNumber())
                        return Double.NaN;
                    int ms = readMilliseconds(scanner.next());
                    if (ms < 0)
                        return Double.NaN;
                    time.addFinal(ms);
                } else if (tz.isExpecting(n)) {
                    tz.minute = n;
                } else if (time.isExpecting(n)) {
                    time.addFinal(n);
                    Token peek = scanner.peek();
                    if (!peek.isEnd() && !peek.isWhiteSpace() && !peek.isKeywordZ() && !peek.isAsciiSign())
                        return Double.NaN;
                } else {
                    if (!day.add(n))
                        return Double.NaN;
                    scanner.skipSymbol('-');
                }
            } else if (token.isKeyword()) {
                KeywordType type = token.keyword;
                int value = token.value;
                if (type == KeywordType.AM_PM && !time.isEmpty()) {
                    time.hourOffset = value;
                } else if (type == KeywordType.MONTH_NAME) {
                    day.namedMonth = value;
                    scanner.skipSymbol('-');
                } else if (type == KeywordType.TIME_ZONE_NAME && hasReadNumber) {
                    tz.set(value);
                } else {
                    if (hasReadNumber)
                        return Double.NaN;
                    if (scanner.peek().isNumber())
                        return Double.NaN;
                }
            } else if (token.isAsciiSign() && (tz.isUTC() || !time.isEmpty())) {
                tz.setSign(token.asciiSign());
                int n = 0;
                int length = 0;
                if (scanner.peek().isNumber()) {
                    Token nt = scanner.next();
                    length = nt.length;
                    n = nt.value;
                }
                hasReadNumber = true;
                if (scanner.peek().isSymbol(':')) {
                    tz.hour = n;
                    tz.minute = NONE;
                } else if (length == 2 || length == 1) {
                    tz.hour = n;
                    tz.minute = 0;
                } else if (length == 4 || length == 3) {
                    tz.hour = n / 100;
                    tz.minute = n % 100;
                } else {
                    return Double.NaN;
                }
            } else if ((token.isAsciiSign() || token.isSymbol(')')) && hasReadNumber) {
                return Double.NaN;
            }
        }

        int[] d = day.write();
        if (d == null)
            return Double.NaN;
        int[] t = time.write();
        if (t == null)
            return Double.NaN;
        Double offset = tz.write();
        if (offset == null)
            return Double.NaN;

        double date = makeDate(makeDay(d[0], d[1], d[2]), makeTime(t[0], t[1], t[2], t[3]));
        if (Double.isNaN(offset)) {
            // local time
            if (!Double.isFinite(date) || Math.abs(date) > 8.64e15 + 864e8)
                return Double.NaN;
            long local = (long) date;
            LocalDateTime l = ldt(local);
            long utc = l.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            date = utc;
        } else {
            date -= offset * 1000.0;
            if (Math.abs(date) > 8.64e15)
                return Double.NaN;
        }
        return timeClip(date);
    }

}
