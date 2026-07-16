/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import java.util.Locale

class CountryUtilsTest {

  @Test
  fun `localeToRegionCode prefers explicit country when it is a real dialing region`() {
    assertThat(CountryUtils.localeToRegionCode(Locale("en", "GB"))).isEqualTo("GB")
    assertThat(CountryUtils.localeToRegionCode(Locale("de", "DE"))).isEqualTo("DE")
    assertThat(CountryUtils.localeToRegionCode(Locale("pt", "BR"))).isEqualTo("BR")
  }

  @Test
  fun `localeToRegionCode falls back to language mapping when country is absent`() {
    assertThat(CountryUtils.localeToRegionCode(Locale("en"))).isEqualTo("US")
    assertThat(CountryUtils.localeToRegionCode(Locale("de"))).isEqualTo("DE")
    assertThat(CountryUtils.localeToRegionCode(Locale("fr"))).isEqualTo("FR")
    assertThat(CountryUtils.localeToRegionCode(Locale("pt"))).isEqualTo("BR")
  }

  @Test
  fun `localeToRegionCode falls back to language mapping when country is not a dialing region`() {
    assertThat(CountryUtils.localeToRegionCode(Locale("en", "ZZ"))).isEqualTo("US")
  }

  @Test
  fun `localeToRegionCode returns null when neither country nor language yields a region`() {
    assertThat(CountryUtils.localeToRegionCode(Locale("xx"))).isNull()
  }
}
