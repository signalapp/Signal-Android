/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import java.util.Currency
import java.util.Locale

class NumberlessRegionMatcherTest {

  private val eur = Currency.getInstance("EUR")
  private val usd = Currency.getInstance("USD")

  @Test
  fun `getRegionsForCallingCodes maps calling codes to regions`() {
    assertThat(NumberlessRegionMatcher.getRegionsForCallingCodes(listOf("31", "49"))).containsExactlyInAnyOrder("NL", "DE")
  }

  @Test
  fun `getRegionsForCallingCodes ignores area code entries, empty entries and numberless marker`() {
    assertThat(NumberlessRegionMatcher.getRegionsForCallingCodes(listOf("1555", "", "99999"))).isEmpty()
    assertThat(NumberlessRegionMatcher.getRegionsForCallingCodes(emptyList())).isEmpty()
  }

  @Test
  fun `getCurrenciesForRegions maps regions to their currencies`() {
    assertThat(NumberlessRegionMatcher.getCurrenciesForRegions(setOf("NL", "DE", "US"))).containsExactlyInAnyOrder(eur, usd)
  }

  @Test
  fun `given locale country and currency in enabled region, when I match, then true`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale("nl", "NL")), eur)).isTrue()
  }

  @Test
  fun `given foreign language but matching locale country, when I match, then true`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale.US, Locale("en", "NL")), eur)).isTrue()
  }

  @Test
  fun `given locale country outside enabled regions, when I match, then false`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale.GERMANY), eur)).isFalse()
  }

  @Test
  fun `given currency not used in enabled regions, when I match, then false`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale("nl", "NL")), usd)).isFalse()
  }

  @Test
  fun `given no selected currency and locale country in enabled region, when I match, then true`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale("nl", "NL")), null)).isTrue()
  }

  @Test
  fun `given no selected currency and locale country outside enabled regions, when I match, then false`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale.US), null)).isFalse()
  }

  @Test
  fun `given country-less locale, when I match, then false`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale("nl")), eur)).isFalse()
    assertThat(NumberlessRegionMatcher.matches(listOf("31"), listOf(Locale("en")), eur)).isFalse()
  }

  @Test
  fun `given empty config, when I match, then false`() {
    assertThat(NumberlessRegionMatcher.matches(emptyList(), listOf(Locale("nl", "NL")), eur)).isFalse()
  }

  @Test
  fun `given only area code entries, when I match, then false`() {
    assertThat(NumberlessRegionMatcher.matches(listOf("1555"), listOf(Locale.US), usd)).isFalse()
  }
}
