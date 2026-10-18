package com.dashjoin.jsonata;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExceptionTest {

  @Test
  public void testError() {
    Jsonata expr = Jsonata.jsonata("$error('message')");
    try {
      expr.evaluate(null);
      Assertions.fail();
    } catch (JException e) {
      Assertions.assertEquals("message", e.getMessage());
    }
  }

  @Test
  public void testDivZero() {
    // like jsonata-js 2.2.2: 1/0 evaluates to Infinity; using it as an operand raises D1001
    Assertions.assertEquals(Double.POSITIVE_INFINITY, Jsonata.jsonata("1 / 0").evaluate(null));
    Jsonata expr = Jsonata.jsonata("1 / 0 + 1");
    try {
      expr.evaluate(null);
      Assertions.fail();
    } catch (JException e) {
      // jsonata-js: JSON.stringify(Infinity) is null
      Assertions.assertEquals("Number out of range: null", e.getMessage());
    }
  }
  
  @Test
  public void testAssertCustomMessage() {
    try {
      Jsonata.jsonata("$assert(false, 'message')").evaluate(null);
      Assertions.fail();
    } catch (JException e) {
      Assertions.assertEquals("message", e.getMessage());
    }
  }
}
