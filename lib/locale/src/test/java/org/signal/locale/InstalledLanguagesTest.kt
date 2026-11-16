/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InstalledLanguagesTest {

  @Test
  fun `a universal APK has every language`() {
    val languages = InstalledLanguages(splitLanguageCodes = null)

    assertThat(languages.contains(Locale.GERMAN)).isTrue()
    assertThat(languages.contains(Locale("ar"))).isTrue()
  }

  @Test
  fun `English is installed without a split`() {
    val languages = splitInstall("de")

    assertThat(languages.contains(Locale.ENGLISH)).isTrue()
    assertThat(languages.contains(Locale.US)).isTrue()
    assertThat(splitInstall().contains(Locale.ENGLISH)).isTrue()
  }

  @Test
  fun `region variants share one split`() {
    val languages = splitInstall("zh", "pt")

    assertThat(languages.contains(Locale.TRADITIONAL_CHINESE)).isTrue()
    assertThat(languages.contains(Locale("pt", "BR"))).isTrue()
    assertThat(languages.contains(Locale.JAPANESE)).isFalse()
  }

  @Test
  fun `legacy and modern language codes match each other`() {
    assertThat(splitInstall("iw").contains(Locale("he"))).isTrue()
    assertThat(splitInstall("he").contains(Locale("iw"))).isTrue()
    assertThat(splitInstall("in").contains(Locale("id"))).isTrue()
    assertThat(splitInstall("ji").contains(Locale("yi"))).isTrue()
  }

  @Test
  fun `Play's installed language tags match`() {
    assertThat(LanguageString.matches("pt-BR", Locale("pt", "BR"))).isTrue()
    assertThat(LanguageString.matches("pt-BR", Locale("pt", "PT"))).isTrue()
    assertThat(LanguageString.matches("DE", Locale.GERMAN)).isTrue()
    assertThat(LanguageString.matches("de", Locale.FRENCH)).isFalse()
  }

  private fun splitInstall(vararg languages: String) = InstalledLanguages(languages.toSet())
}
