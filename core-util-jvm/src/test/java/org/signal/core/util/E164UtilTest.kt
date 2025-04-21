/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.Assert
import org.junit.Test

class E164UtilTest {

  @Test
  fun `formatAsE164WithCountryCodeForDisplay - generic`() {
    // UK
    Assert.assertEquals("+442079460018", E164Util.formatAsE164WithCountryCodeForDisplay("44", "(020) 7946 0018"))
    Assert.assertEquals("+442079460018", E164Util.formatAsE164WithCountryCodeForDisplay("44", "+442079460018"))

    // CH
    Assert.assertEquals("+41446681800", E164Util.formatAsE164WithCountryCodeForDisplay("41", "+41 44 668 18 00"))
    Assert.assertEquals("+41446681800", E164Util.formatAsE164WithCountryCodeForDisplay("41", "+41 (044) 6681800"))

    // DE
    Assert.assertEquals("+4930123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "0049 030 123456"))
    Assert.assertEquals("+4930123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "0049 (0)30123456"))
    Assert.assertEquals("+4930123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "0049((0)30)123456"))
    Assert.assertEquals("+4930123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "+49 (0) 30  1 2  3 45 6 "))
    Assert.assertEquals("+4930123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "030 123456"))

    // DE
    Assert.assertEquals("+49171123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "0171123456"))
    Assert.assertEquals("+49171123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "0171/123456"))
    Assert.assertEquals("+49171123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "+490171/123456"))
    Assert.assertEquals("+49171123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "00490171/123456"))
    Assert.assertEquals("+49171123456", E164Util.formatAsE164WithCountryCodeForDisplay("49", "0049171/123456"))
  }

  @Test
  fun `formatAsE164 - generic`() {
    var formatter: E164Util.Formatter = E164Util.createFormatterForE164("+14152222222")
    Assert.assertEquals("+14151111122", formatter.formatAsE164("(415) 111-1122"))
    Assert.assertEquals("+14151111123", formatter.formatAsE164("(415) 111 1123"))
    Assert.assertEquals("+14151111124", formatter.formatAsE164("415-111-1124"))
    Assert.assertEquals("+14151111125", formatter.formatAsE164("415.111.1125"))
    Assert.assertEquals("+14151111126", formatter.formatAsE164("+1 415.111.1126"))
    Assert.assertEquals("+14151111127", formatter.formatAsE164("+1 415 111 1127"))
    Assert.assertEquals("+14151111128", formatter.formatAsE164("+1 (415) 111 1128"))
    Assert.assertEquals("911", formatter.formatAsE164("911"))
    Assert.assertEquals("+4567890", formatter.formatAsE164("+456-7890"))

    formatter = E164Util.createFormatterForE164("+442079460010")
    Assert.assertEquals("+442079460018", formatter.formatAsE164("(020) 7946 0018"))
  }

  @Test
  fun `formatAsE164 - strip leading zeros`() {
    val formatter: E164Util.Formatter = E164Util.createFormatterForE164("+14152222222")
    Assert.assertEquals("+15551234567", formatter.formatAsE164("+015551234567"))
    Assert.assertEquals("+15551234567", formatter.formatAsE164("+0015551234567"))
    Assert.assertEquals("+15551234567", formatter.formatAsE164("01115551234567"))
    Assert.assertEquals("1234", formatter.formatAsE164("01234"))
    Assert.assertEquals(null, formatter.formatAsE164("0"))
    Assert.assertEquals(null, formatter.formatAsE164("0000000"))
  }

  @Test
  fun `formatAsE164 - US mix`() {
    val formatter: E164Util.Formatter = E164Util.createFormatterForE164("+16105880522")

    Assert.assertEquals("+551234567890", formatter.formatAsE164("+551234567890"))
    Assert.assertEquals("+11234567890", formatter.formatAsE164("(123) 456-7890"))
    Assert.assertEquals("+11234567890", formatter.formatAsE164("1234567890"))
    Assert.assertEquals("+16104567890", formatter.formatAsE164("456-7890"))
    Assert.assertEquals("+16104567890", formatter.formatAsE164("4567890"))
    Assert.assertEquals("+11234567890", formatter.formatAsE164("011 1 123 456 7890"))
    Assert.assertEquals("+5511912345678", formatter.formatAsE164("0115511912345678"))
    Assert.assertEquals("+16105880522", formatter.formatAsE164("+16105880522"))
  }

  @Test
  fun `formatAsE164 - Brazil mix`() {
    val formatter: E164Util.Formatter = E164Util.createFormatterForE164("+5521912345678")

    Assert.assertEquals("+16105880522", formatter.formatAsE164("+16105880522"))
    Assert.assertEquals("+552187654321", formatter.formatAsE164("8765 4321"))
    Assert.assertEquals("+5521987654321", formatter.formatAsE164("9 8765 4321"))
    Assert.assertEquals("+552287654321", formatter.formatAsE164("22 8765 4321"))
    Assert.assertEquals("+5522987654321", formatter.formatAsE164("22 9 8765 4321"))
    Assert.assertEquals("+551234567890", formatter.formatAsE164("+55 (123) 456-7890"))
    Assert.assertEquals("+14085048577", formatter.formatAsE164("002214085048577"))
    Assert.assertEquals("+5511912345678", formatter.formatAsE164("011912345678"))
    Assert.assertEquals("+5511912345678", formatter.formatAsE164("02111912345678"))
    Assert.assertEquals("+551234567", formatter.formatAsE164("1234567"))
    Assert.assertEquals("+5521912345678", formatter.formatAsE164("+5521912345678"))
    Assert.assertEquals("+552112345678", formatter.formatAsE164("+552112345678"))
  }

  @Test
  fun `formatAsE164 - short codes`() {
    val formatter: E164Util.Formatter = E164Util.createFormatterForE164("+14152222222")
    Assert.assertEquals("40404", formatter.formatAsE164("40404"))
    Assert.assertEquals("40404", formatter.formatAsE164("40404"))
    Assert.assertEquals("7726", formatter.formatAsE164("7726"))
    Assert.assertEquals("22000", formatter.formatAsE164("22000"))
    Assert.assertEquals("265080", formatter.formatAsE164("265080"))
    Assert.assertEquals("32665", formatter.formatAsE164("32665"))
    Assert.assertEquals("732873", formatter.formatAsE164("732873"))
    Assert.assertEquals("73822", formatter.formatAsE164("73822"))
    Assert.assertEquals("83547", formatter.formatAsE164("83547"))
    Assert.assertEquals("84639", formatter.formatAsE164("84639"))
    Assert.assertEquals("89887", formatter.formatAsE164("89887"))
    Assert.assertEquals("99000", formatter.formatAsE164("99000"))
    Assert.assertEquals("911", formatter.formatAsE164("911"))
    Assert.assertEquals("112", formatter.formatAsE164("112"))
    Assert.assertEquals("311", formatter.formatAsE164("311"))
    Assert.assertEquals("611", formatter.formatAsE164("611"))
    Assert.assertEquals("988", formatter.formatAsE164("988"))
    Assert.assertEquals("999", formatter.formatAsE164("999"))
    Assert.assertEquals("118", formatter.formatAsE164("118"))
  }

  @Test
  fun `formatAsE164 - invalid`() {
    val formatter: E164Util.Formatter = E164Util.createFormatterForE164("+14152222222")
    Assert.assertEquals(null, formatter.formatAsE164("junk@junk.net"))
    Assert.assertEquals(null, formatter.formatAsE164("__textsecure_group__!foobar"))
    Assert.assertEquals(null, formatter.formatAsE164("bonbon"))
    Assert.assertEquals(null, formatter.formatAsE164("44444444441234512312312312312312312312"))
    Assert.assertEquals(null, formatter.formatAsE164("144444444441234512312312312312312312312"))
    Assert.assertEquals(null, formatter.formatAsE164("1"))
    Assert.assertEquals(null, formatter.formatAsE164("55"))
    Assert.assertEquals(null, formatter.formatAsE164("0"))
    Assert.assertEquals(null, formatter.formatAsE164("000"))
  }

  @Test
  fun `formatAsE164 - no local number`() {
    val formatter: E164Util.Formatter = E164Util.createFormatterForRegionCode("US")
    Assert.assertEquals("+14151111122", formatter.formatAsE164("(415) 111-1122"))
  }
}
