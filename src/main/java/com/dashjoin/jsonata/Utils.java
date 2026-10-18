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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.dashjoin.jsonata.Jsonata.JFunction;
import com.dashjoin.jsonata.Jsonata.JFunctionCallable;

@SuppressWarnings({"rawtypes"})
public class Utils {
    public static boolean isNumeric(Object v) {
        if (v instanceof Long) return true;
        boolean isNum = false;
        if (v instanceof Number) {
            double d = ((Number)v).doubleValue();
            isNum = !Double.isNaN(d);
            if (isNum && !Double.isFinite(d)) {
                throw new JException("D1001", 0, v);
            }
        }
        return isNum;
    }

    public static boolean isArrayOfStrings(Object v) {
        boolean result = false;
        if (v instanceof Collection) {
            for (Object o : ((Collection)v))
                if (!(o instanceof String))
                    return false;
            return true;
        }
        return false;
    }
    public static boolean isArrayOfNumbers(Object v) {
        boolean result = false;
        if (v instanceof Collection) {
            for (Object o : ((Collection)v))
                if (!isNumeric(o))
                    return false;
            return true;
        }
        return false;
    }

    /**
     * Deep equality as in jsonata-js utils.isDeepEqual: numbers are compared by value regardless of
     * their Java type (Integer, Long, Double...), arrays element-wise, objects by key set and values.
     */
    public static boolean isDeepEqual(Object lhs, Object rhs) {
        if (lhs == rhs)
            return true;
        if (lhs == null || rhs == null)
            return false;
        if (lhs == Jsonata.NULL_VALUE || rhs == Jsonata.NULL_VALUE)
            return false;
        if (lhs instanceof Number && rhs instanceof Number)
            return ((Number) lhs).doubleValue() == ((Number) rhs).doubleValue();
        if (lhs instanceof List && rhs instanceof List) {
            List<?> l = (List<?>) lhs, r = (List<?>) rhs;
            if (l.size() != r.size())
                return false;
            for (int i = 0; i < l.size(); i++)
                if (!isDeepEqual(nullValue(l.get(i)), nullValue(r.get(i))))
                    return false;
            return true;
        }
        if (lhs instanceof Map && rhs instanceof Map) {
            Map<?, ?> l = (Map<?, ?>) lhs, r = (Map<?, ?>) rhs;
            if (l.size() != r.size() || !l.keySet().equals(r.keySet()))
                return false;
            for (Map.Entry<?, ?> e : l.entrySet())
                if (!isDeepEqual(nullValue(e.getValue()), nullValue(r.get(e.getKey()))))
                    return false;
            return true;
        }
        return lhs.equals(rhs);
    }

    /**
     * Returns a key whose equals/hashCode is consistent with {@link #isDeepEqual(Object, Object)}
     * (numbers normalized to Double, -0.0 to 0.0, Java null to NULL_VALUE).
     */
    public static Object deepEqualKey(Object o) {
        if (o == null)
            return Jsonata.NULL_VALUE;
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            return d == 0 ? 0.0d : d;
        }
        if (o instanceof List) {
            List<Object> l = new ArrayList<>(((List<?>) o).size());
            for (Object x : (List<?>) o)
                l.add(deepEqualKey(x));
            return l;
        }
        if (o instanceof Map) {
            Map<Object, Object> m = new java.util.HashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet())
                m.put(e.getKey(), deepEqualKey(e.getValue()));
            return m;
        }
        return o;
    }

    /** Java null inside input structures represents JSON null */
    private static Object nullValue(Object o) {
        return o == null ? Jsonata.NULL_VALUE : o;
    }

    /**
     * Formats a double exactly like ECMAScript Number.prototype.toString
     * (shortest round-trip digits, plain notation for 1e-7 &lt; |d| &lt; 1e21, otherwise "1e+21" style).
     */
    public static String jsNumberToString(double d) {
        if (Double.isNaN(d)) return "NaN";
        if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
        if (d == 0) return "0";
        String sign = d < 0 ? "-" : "";
        // Double.toString gives the shortest uniquely identifying digits (JDK 19+, JDK-4511638);
        // on older JDKs the result may contain one superfluous digit in rare cases
        java.math.BigDecimal bd = new java.math.BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        if (bd.precision() == 2) {
            // Double.toString always prints two digits in E notation (4.9E-324); JS prints 5e-324
            java.math.BigDecimal one = bd.round(new java.math.MathContext(1, java.math.RoundingMode.HALF_EVEN));
            if (one.doubleValue() == Math.abs(d))
                bd = one.stripTrailingZeros();
        }
        String digits = bd.unscaledValue().toString();
        int k = digits.length();
        int n = k - bd.scale();
        StringBuilder b = new StringBuilder(sign);
        if (k <= n && n <= 21) {
            b.append(digits);
            for (int z = 0; z < n - k; z++) b.append('0');
        } else if (0 < n && n <= 21) {
            b.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            b.append("0.");
            for (int z = 0; z < -n; z++) b.append('0');
            b.append(digits);
        } else {
            int e = n - 1;
            b.append(digits.charAt(0));
            if (k > 1) b.append('.').append(digits, 1, k);
            b.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
        }
        return b.toString();
    }

    /**
     * Formats an integral double exactly like ECMAScript Number.prototype.toString(radix)
     * (V8 DoubleToRadixCString: digits beyond the precision of the double are zeros).
     */
    public static String jsIntegerToString(double value, int radix) {
        if (radix == 10 || Double.isNaN(value) || Double.isInfinite(value))
            return jsNumberToString(value);
        if (value % 1 != 0)
            throw new IllegalArgumentException("not an integer: " + value);
        final String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        boolean negative = value < 0;
        double integer = Math.abs(value);
        StringBuilder b = new StringBuilder();
        // fill unrepresented digits with zero
        while (Math.getExponent(integer / radix) - 52 > 0) {
            integer /= radix;
            b.append('0');
        }
        do {
            double remainder = integer % radix;
            b.append(chars.charAt((int) remainder));
            integer = (integer - remainder) / radix;
        } while (integer > 0);
        if (negative)
            b.append('-');
        return b.reverse().toString();
    }

    public static boolean isFunction(Object o) {
        return o instanceof JFunction || o instanceof JFunctionCallable;
    }

    static Object NONE = new Object();

    /**
     * Create an empty sequence to contain query results
     * @returns {Array} - empty sequence
     */
    public static List<Object> __createSequence() { return __createSequence(NONE); }

    public static List<Object> __createSequence(Object el) {
        JList<Object> sequence = new JList<>();
        sequence.sequence = true;
        if (el!=NONE) {
            sequence.add(el);
        }
        return sequence;
    }

    public static class JList<E> extends ArrayList<E> {
        public JList() { super(); }
        public JList(int capacity) { super(capacity); }
        public JList(Collection<? extends E> c) {
            super(c);
        }

        // Jsonata specific flags
        public boolean sequence;

        public boolean outerWrapper;

        public boolean tupleStream;

        public boolean keepSingleton;

        public boolean cons;

        protected int maxSize = Integer.MAX_VALUE;

        // Limit the max size of sequences
        public void setMaxSize(int _maxSize) {
            maxSize = _maxSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        @Override
        public boolean add(E e) {
            if (size()>=maxSize)
                throw new JException("D2015", -1, maxSize);
            return super.add(e);
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            if (size()+c.size()>maxSize)
                throw new JException("D2015", -1, maxSize);
            return super.addAll(c);
        }
    }

    public static boolean isSequence(Object result) {
        return result instanceof JList && ((JList)result).sequence;
    }

    /**
     * List representing an int range [a,b]
     * Both sides are included. Read-only + immutable.
     * 
     * Used for late materialization of ranges.
     */
    public static class RangeList extends AbstractList<Number> {

        final long a, b;
        final int size;

        public RangeList(long left, long right) {
            assert(left<=right);
            assert(right-left < Integer.MAX_VALUE);
            a = left; b = right;
            size = (int) (b-a+1);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean addAll(Collection<? extends Number> c) {
            throw new UnsupportedOperationException("RangeList does not support 'addAll'");
        }

        @Override
        public Number get(int index) {
            if (index < size) {
                try {
                    return Utils.convertNumber( a + index );
                } catch (JException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            throw new IndexOutOfBoundsException(index);
        }        
    }
     
    public static Number convertNumber(Number n) {
        // Use long if the number is not fractional
        if (!isNumeric(n)) return null;
        if (n.longValue()==n.doubleValue()) {
            long l = n.longValue();
            if (((int)l)==l)
                return (int)l;
            else
                return l;
        }
        return n.doubleValue();
    }

    public static void checkUrl(String str) {
      boolean isHigh = false;
      for ( int i=0; i<str.length(); i++) {
        boolean wasHigh = isHigh;
        isHigh = Character.isHighSurrogate(str.charAt(i));
        if (wasHigh && isHigh)
          throw new JException("Malformed URL", i);
      }
      if (isHigh)
        throw new JException("Malformed URL", 0);
    }

    static Object convertValue(Object val) {
        return val != Jsonata.NULL_VALUE ? val : null;
    }

    static void convertNulls(Map<String, Object> res) {
        for (Entry<String, Object> e : res.entrySet()) {
            Object val = e.getValue();
            Object l = convertValue(val);
            if (l!=val)
                e.setValue(l);
            recurse(val);
        }
    }

    static void convertNulls(List<Object> res) {
        for (int i=0; i<res.size(); i++) {
            Object val = res.get(i);
            Object l = convertValue(val);
            if (l!=val)
                res.set(i, l);
            recurse(val);
        }
    }

    static void recurse(Object val) {
        if (val instanceof Map)
            convertNulls((Map)val);
        if (val instanceof List)
            convertNulls((List)val);
    }

    /**
     * Converts all occurrences of {@link Jsonata#NULL_VALUE} in the given object to null
     * 
     * @param res Result object which might contain NULL_VALUEs
     * @return The same object with NULL_VALUEs replaced with null
     */
    public static Object convertNulls(Object res) {
        recurse(res);
        return convertValue(res);
    }
    
    /**
     * adapted from org.json.JSONObject https://github.com/stleary/JSON-java
     */
    public static void quote(String string, StringBuilder w) {
      char b;
      char c = 0;
      String hhhh;
      int i;
      int len = string.length();

      for (i = 0; i < len; i += 1) {
          b = c;
          c = string.charAt(i);
          switch (c) {
          case '\\':
          case '"':
              w.append('\\');
              w.append(c);
              break;
          /*
          case '/':
              if (b == '<') {
                  w.append('\\');
              }
              w.append(c);
              break;
          */
          case '\b':
              w.append("\\b");
              break;
          case '\t':
              w.append("\\t");
              break;
          case '\n':
              w.append("\\n");
              break;
          case '\f':
              w.append("\\f");
              break;
          case '\r':
              w.append("\\r");
              break;
          default:
              // like JSON.stringify: escape control characters and lone surrogates only
              boolean lone = Character.isHighSurrogate(c) ? !(i + 1 < len && Character.isLowSurrogate(string.charAt(i + 1)))
                      : Character.isLowSurrogate(c) && !(i > 0 && Character.isHighSurrogate(string.charAt(i - 1)));
              if (c < ' ' || lone) {
                  w.append("\\u");
                  hhhh = Integer.toHexString(c);
                  w.append("0000", 0, 4 - hhhh.length());
                  w.append(hhhh);
              } else {
                  w.append(c);
              }
          }
      }
    }

    /**
     * version of Map.of(key, value) that allows value to be null
     */
    static Map mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
    
    /**
     * called from Jsonata.compare() - aa and bb are either both strings or numbers
     * for numbers, compare doubleValue to avoid cast error when int is compared to double
     */
    @SuppressWarnings("unchecked")
    static int compareTo(Object aa, Object bb) {
        if (aa instanceof String)
            return ((Comparable)aa).compareTo(bb);
        else
        {
            // like JS < and >: numbers of any Java type by value, -0 equals 0
            double a = ((Number)aa).doubleValue(), b = ((Number)bb).doubleValue();
            return a < b ? -1 : (a > b ? 1 : 0);
        }
    }
}
