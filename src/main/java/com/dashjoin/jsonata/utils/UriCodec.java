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

import java.nio.charset.StandardCharsets;

/**
 * ECMAScript encodeURI / encodeURIComponent / decodeURI / decodeURIComponent
 * (ECMA-262 "URI Handling Functions"), as used by the JSONata URL functions.
 * Malformed input raises {@link IllegalArgumentException} (JavaScript URIError).
 */
public final class UriCodec {
    private UriCodec() {}

    private static final String ALNUM_MARK = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()";
    private static final String URI_RESERVED_HASH = ";/?:@&=+$,#";

    public static String encodeURIComponent(String s) {
        return encode(s, ALNUM_MARK);
    }

    public static String encodeURI(String s) {
        return encode(s, ALNUM_MARK + URI_RESERVED_HASH);
    }

    public static String decodeURIComponent(String s) {
        return decode(s, "");
    }

    public static String decodeURI(String s) {
        return decode(s, URI_RESERVED_HASH);
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static String encode(String s, String unescaped) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        int len = s.length();
        for (int k = 0; k < len; k++) {
            char c = s.charAt(k);
            if (c < 128 && unescaped.indexOf(c) >= 0) {
                b.append(c);
                continue;
            }
            int cp;
            if (Character.isLowSurrogate(c))
                throw new IllegalArgumentException("URI malformed");
            if (Character.isHighSurrogate(c)) {
                if (k + 1 >= len || !Character.isLowSurrogate(s.charAt(k + 1)))
                    throw new IllegalArgumentException("URI malformed");
                cp = Character.toCodePoint(c, s.charAt(++k));
            } else
                cp = c;
            for (byte x : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8))
                b.append('%').append(HEX[(x >> 4) & 0xF]).append(HEX[x & 0xF]);
        }
        return b.toString();
    }

    static int hexByte(String s, int k) {
        if (k + 2 >= s.length() || s.charAt(k) != '%')
            throw new IllegalArgumentException("URI malformed");
        int h = Character.digit(s.charAt(k + 1), 16), l = Character.digit(s.charAt(k + 2), 16);
        if (h < 0 || l < 0)
            throw new IllegalArgumentException("URI malformed");
        return (h << 4) | l;
    }

    static String decode(String s, String reserved) {
        StringBuilder b = new StringBuilder(s.length());
        int len = s.length();
        for (int k = 0; k < len; k++) {
            char c = s.charAt(k);
            if (c != '%') {
                b.append(c);
                continue;
            }
            int start = k;
            if (k + 2 >= len)
                throw new IllegalArgumentException("URI malformed");
            int B = hexByte(s, k);
            k += 2;
            if ((B & 0x80) == 0) {
                char ch = (char) B;
                if (reserved.indexOf(ch) >= 0)
                    b.append(s, start, k + 1);
                else
                    b.append(ch);
                continue;
            }
            int n;
            if ((B & 0xE0) == 0xC0) n = 2;
            else if ((B & 0xF0) == 0xE0) n = 3;
            else if ((B & 0xF8) == 0xF0) n = 4;
            else throw new IllegalArgumentException("URI malformed");
            int cp = B & (0xFF >> (n + 1));
            if (k + 3 * (n - 1) >= len)
                throw new IllegalArgumentException("URI malformed");
            for (int j = 1; j < n; j++) {
                k++;
                int x = hexByte(s, k);
                if ((x & 0xC0) != 0x80)
                    throw new IllegalArgumentException("URI malformed");
                cp = (cp << 6) | (x & 0x3F);
                k += 2;
            }
            int min = n == 2 ? 0x80 : n == 3 ? 0x800 : 0x10000;
            if (cp < min || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF))
                throw new IllegalArgumentException("URI malformed");
            b.appendCodePoint(cp);
        }
        return b.toString();
    }
}
