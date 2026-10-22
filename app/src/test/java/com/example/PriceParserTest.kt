package com.example

import com.example.data.PriceParser
import com.example.data.PriceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceParserTest {

  private fun dollars(input: String): Double = (PriceParser.parse(input) as PriceResult.Valid).dollars

  @Test
  fun `accepts common ways of typing a price`() {
    assertEquals(8499.0, dollars("8499"), 0.0)
    assertEquals(8499.0, dollars("8,499"), 0.0)
    assertEquals(8499.0, dollars("$8499"), 0.0)
    assertEquals(8499.99, dollars(" $8,499.99 "), 0.0)
  }

  @Test
  fun `refuses instead of silently becoming zero`() {
    listOf("", "   ", "eighty-five hundred", "84.999", "12abc", "-50", "0").forEach {
      assertTrue("expected refusal for '$it'", PriceParser.parse(it) is PriceResult.Invalid)
    }
  }

  @Test
  fun `refusal messages are plain words`() {
    val msg = (PriceParser.parse("abc") as PriceResult.Invalid).message
    assertTrue(msg.contains("isn't a number"))
    assertTrue(!msg.contains("Exception"))
  }
}
