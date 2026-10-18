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
package com.dashjoin.jsonata;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.regex.Matcher;

public class JException extends RuntimeException {

    private static final long serialVersionUID = -3354943281127831704L;
    
    String error;
    int location;
    Object current;
    Object expected;

    public JException(String error) {
        this(error, -1, null, null);
    }
    public JException(String error, int location) {
        this(error, location, null, null);
    }
    public JException(String error, int location, Object currentToken) {
        this(error, location, currentToken, null);
    }
    public JException(String error, int location, Object currentToken, Object expected) {
        this(null, error, location, currentToken, expected);
    }
    /**
     * Error with more than two message arguments (in the order of the placeholders of the
     * message template, e.g. T2009: value, value2, token)
     */
    public JException(String error, int location, Object arg1, Object arg2, Object... more) {
        super(msg(error, location, concat(arg1, arg2, more), false), null);
        this.error = error; this.location = location;
        this.current = arg1;
        this.expected = arg2;
    }

    static Object[] concat(Object a, Object b, Object[] more) {
        Object[] r = new Object[2 + more.length];
        r[0] = a; r[1] = b;
        System.arraycopy(more, 0, r, 2, more.length);
        return r;
    }

    public JException(Throwable cause, String error, int location, Object currentToken, Object expected) {
        super(msg(error, location, currentToken, expected), cause);
        this.error = error; this.location = location;
        this.current = currentToken;
        this.expected = expected;
    }

    /**
     * Returns the error code, i.e. S0201
     * @return
     */
    public String getError() {
        return error;
    }

    /**
     * Returns the error location (in characters)
     * @return
     */
    public int getLocation() {
        return location;
    }

    /**
     * Returns the current token
     * @return
     */
    public Object getCurrent() {
        return current;
    }

    /**
     * Returns the expected token
     * @return
     */
    public Object getExpected() {
        return expected;
    }

    /**
     * Returns the error message with error details in the text.
     * Example: Syntax error: ")" {code=S0201 position=3}
     * @return
     */
    public String getDetailedErrorMessage() {
        return msg(error, location, current, expected, true);
    }

    /**
     * Generate error message from given error code
     * Codes are defined in Jsonata.errorCodes
     * 
     * Fallback: if error code does not exist, return a generic message
     * 
     * @param error
     * @param location
     * @param arg1
     * @param arg2
     * @return
     */
    public static String msg(String error, int location, Object arg1, Object arg2) {
        return msg(error, location, arg1, arg2, false);
    }

    /**
     * Generate error message from given error code
     * Codes are defined in Jsonata.errorCodes
     * 
     * Fallback: if error code does not exist, return a generic message
     * 
     * @param error
     * @param location
     * @param arg1
     * @param arg2
     * @param details True = add error details as text, false = don't add details (use getters to retrieve details)
     * @return
     */
    public static String msg(String error, int location, Object arg1, Object arg2, boolean details) {
        return msg(error, location, new Object[] { arg1, arg2 }, details);
    }

    static String msg(String error, int location, Object[] args, boolean details) {
        Object arg1 = args.length > 0 ? args[0] : null, arg2 = args.length > 1 ? args[1] : null;
        String message = Jsonata.errorCodes.get(error);

        if (message==null) {
            // unknown error code
            return "JSonataException "+error+
                (details ? " {code=unknown position="+location+" arg1="+arg1+" arg2="+arg2+"}" : "");
        }

        // like jsonata-js populateMessage: {{{x}}} is replaced by the raw value, {{x}} by its
        // JSON representation; the values are taken in order from arg1, arg2
        Matcher m = PLACEHOLDER.matcher(message);
        StringBuilder sb = new StringBuilder();
        int k = 0;
        while (m.find()) {
            Object v = k < args.length ? args[k++] : null;
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) != null ? raw(v) : json(v)));
        }
        m.appendTail(sb);
        String formatted = sb.toString();
        if (details) {
            formatted = formatted + " {code="+error;
            if (location>=0)
                formatted += " position="+location;
            formatted += "}";
        }
        return formatted;
    }

    static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{\\{\\{([^}]+)\\}\\}\\}|\\{\\{([^}]+)\\}\\}");

    /** JavaScript String(value) */
    static String raw(Object v) {
        if (v == null) return "undefined";
        if (v == Jsonata.NULL_VALUE) return "null";
        if (v instanceof Number) return Utils.jsNumberToString(((Number) v).doubleValue());
        if (v instanceof java.util.Map) return "[object Object]";
        if (v instanceof List) {
            StringBuilder b = new StringBuilder();
            boolean first = true;
            for (Object o : (List<?>) v) {
                if (!first) b.append(',');
                first = false;
                b.append(o == null || o == Jsonata.NULL_VALUE ? "" : raw(o));
            }
            return b.toString();
        }
        return String.valueOf(v);
    }

    /** JavaScript JSON.stringify(value) (undefined renders as "undefined" in a message) */
    static String json(Object v) {
        if (v == null) return "undefined";
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            return Double.isFinite(d) ? Utils.jsNumberToString(d) : "null";
        }
        if (v instanceof String) {
            StringBuilder b = new StringBuilder("\"");
            Utils.quote((String) v, b);
            return b.append('"').toString();
        }
        if (v instanceof Boolean || v == Jsonata.NULL_VALUE) return String.valueOf(v);
        if (v instanceof java.util.Map || v instanceof List) {
            try {
                return Functions.string(v, false);
            } catch (RuntimeException e) {
                return String.valueOf(v);
            }
        }
        if (Utils.isFunction(v) || Functions.isLambda(v)) return "undefined";
        return "\"" + v + "\"";
    }

    // Recover
    String type;
    List<Tokenizer.Token> remaining;
}
