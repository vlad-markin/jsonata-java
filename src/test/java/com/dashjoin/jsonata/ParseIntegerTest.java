package com.dashjoin.jsonata;

import static com.dashjoin.jsonata.Jsonata.jsonata;
import static java.util.Arrays.asList;
import static java.util.Map.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ParseIntegerTest {

    @Test
    public void parseIntegerNoError() {
        Jsonata expr = jsonata("$parseInteger('xyz','000')");
        var res = expr.evaluate(null);
        // like jsonata-js (parseInt of a non-numeric string)
        assertEquals(Double.NaN, res);
    }
    
    @Test
    public void parseIntegerError() {
        // unsupported numbering sequence: D3130 like in jsonata-js
        assertThrows(JException.class, () -> {
            Jsonata expr = jsonata("$parseInteger('000','xyz')");
            var res = expr.evaluate(null);
            assertEquals(res, null);
        } );
    }
}
