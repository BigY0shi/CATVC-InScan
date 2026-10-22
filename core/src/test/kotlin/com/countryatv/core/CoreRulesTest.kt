package com.countryatv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemIdTest {
  @Test fun `ids are valid, unique and sort by time`() {
    val a = ItemId.new(1_000L)
    val b = ItemId.new(2_000L)
    assertTrue(ItemId.isValid(a) && ItemId.isValid(b))
    assertNotEquals(ItemId.new(1_000L), ItemId.new(1_000L))
    assertTrue(a < b)
    val many = (1..10_000).map { ItemId.new() }.toSet()
    assertEquals(10_000, many.size)
  }

  @Test fun `rejects anything that is not an id`() {
    listOf("", "abc", "01ARZ3NDEKTSV4RRFFQ69G5FAI", "01arz3ndektsv4rrffq69g5fav", "1M8GDM9AXKP042788").forEach {
      assertTrue(!ItemId.isValid(it), it)
    }
  }
}

class VinTest {
  @Test fun `known good VINs pass the check digit`() {
    assertIs<VinCheck.Ok>(Vin.check("1M8GDM9AXKP042788"))  // check digit X
    assertIs<VinCheck.Ok>(Vin.check("11111111111111111"))
    assertIs<VinCheck.Ok>(Vin.check(" 1m8gdm9axkp042788 "))   // normalised
  }

  @Test fun `a typo fails and says what to do`() {
    val r = Vin.check("1M8GDM9A1KP042788")
    assertIs<VinCheck.Fail>(r)
    assertTrue(r.message.contains("should be X"))
  }

  @Test fun `letters I O Q are refused`() {
    assertIs<VinCheck.Fail>(Vin.check("1M8GDM9AXKP04278O"))
  }

  @Test fun `short serials are not judged by the road VIN rule`() {
    assertIs<VinCheck.NotApplicable>(Vin.check("JH2PE04A"))
  }
}

class MoneyTest {
  private fun cents(s: String) = (Money.parseDollars(s) as MoneyResult.Valid).cents

  @Test fun `common ways of typing a price become exact cents`() {
    assertEquals(849_900L, cents("8499"))
    assertEquals(849_900L, cents("8,499"))
    assertEquals(849_900L, cents("$8499"))
    assertEquals(849_999L, cents(" $8,499.99 "))
    assertEquals(849_950L, cents("8499.5"))
  }

  @Test fun `unclear amounts are refused, never zero`() {
    listOf("", "eighty-five hundred", "84.999", "-50", "0", "0.00").forEach {
      assertIs<MoneyResult.Invalid>(Money.parseDollars(it), it)
    }
  }

  @Test fun `formats as dollars`() {
    assertEquals("$8,499.99", Money.format(849_999L))
  }
}

class LabelPayloadTest {
  @Test fun `round trips, including awkward characters`() {
    val c = LabelContent(ItemId.new(), "Can-Am", "Maverick X3; DS=Turbo", "100% RR", "2025", "Red", "Side-by-Side (UTV)", "1M8GDM9AXKP042788")
    assertEquals(c, LabelPayload.decode(LabelPayload.encode(c)))
  }

  @Test fun `never carries price or customer data`() {
    val encoded = LabelPayload.encode(LabelContent(ItemId.new(), make = "Honda"))
    assertTrue(!encoded.contains("price") && !encoded.contains("$"))
  }

  @Test fun `ignores codes that are not ours`() {
    assertNull(LabelPayload.decode("1M8GDM9AXKP042788"))
    assertNull(LabelPayload.decode("https://example.org"))
    assertNull(LabelPayload.decode("CATV1;mk=Honda"))           // no id
    assertNull(LabelPayload.decode("CATV1;id=not-an-id"))
  }
}

class UnitStatusTest {
  @Test fun `only the allowed moves are allowed`() {
    assertTrue(UnitStatus.INCOMING.canMoveTo(UnitStatus.IN_STOCK))
    assertTrue(!UnitStatus.INCOMING.canMoveTo(UnitStatus.SOLD))
    assertTrue(!UnitStatus.RETIRED.canMoveTo(UnitStatus.IN_STOCK))
  }
}
