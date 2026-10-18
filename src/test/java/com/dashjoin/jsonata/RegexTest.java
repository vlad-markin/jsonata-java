package com.dashjoin.jsonata;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class RegexTest {
    @Test
    public void testRegex() {
        var expression = Jsonata.jsonata("/^test.*$/");
        Object evaluate = expression.evaluate(null);
        String expected = "^test.*$";
        // Pattern.toString() is the translated java.util.regex pattern, JsRegex.source() the JS source
        Assertions.assertEquals(expected, com.dashjoin.jsonata.utils.JsRegex.source((java.util.regex.Pattern) evaluate));
    }

    @Test
    public void testEvalRegex() {
        var expression = Jsonata.jsonata("$eval('/^test.*$/')");
        Object evaluate = expression.evaluate(null);
        String expected = "^test.*$";
        // Pattern.toString() is the translated java.util.regex pattern, JsRegex.source() the JS source
        Assertions.assertEquals(expected, com.dashjoin.jsonata.utils.JsRegex.source((java.util.regex.Pattern) evaluate));
    }

    @Test
    public void testEvalRegexCheckAnswerData() {
        var expression = Jsonata.jsonata(
                "(\n" +
                        "    $matcher := $eval('/l/');\n" +
                        "    ('Hello World' ~> $matcher);\n" +
                        ")"
        );
        Map<String, Object> evaluate = (Map<String, Object>)(expression.evaluate(null));
        Assertions.assertEquals("l", evaluate.get("match"));
        Assertions.assertEquals(2, evaluate.get("start"));
        Assertions.assertEquals(3, evaluate.get("end"));
        // like jsonata-js: groups holds the capturing groups only
        Assertions.assertEquals(List.of(), evaluate.get("groups"));
        Assertions.assertInstanceOf(Jsonata.Fn0.class, evaluate.get("next"));
    }

    @Test
    public void testEvalRegexCallNextAndCheckResult() {
        var expression = Jsonata.jsonata(
                "(\n" +
                        "    $matcher := $eval('/l/');\n" +
                        "    ('Hello World' ~> $matcher).next();\n" +
                        ")"
        );
        Map<String, Object> evaluate = (Map<String, Object>)(expression.evaluate(null));
        Assertions.assertEquals("l", evaluate.get("match"));
        Assertions.assertEquals(3, evaluate.get("start"));
        Assertions.assertEquals(4, evaluate.get("end"));
        // like jsonata-js: groups holds the capturing groups only
        Assertions.assertEquals(List.of(), evaluate.get("groups"));
        Assertions.assertInstanceOf(Jsonata.Fn0.class, evaluate.get("next"));
    }
}
