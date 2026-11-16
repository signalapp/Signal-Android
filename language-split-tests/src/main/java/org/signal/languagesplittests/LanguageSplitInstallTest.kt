/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.languagesplittests

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.languagesplittests.LanguageSplitDevice.addLanguages
import org.signal.languagesplittests.LanguageSplitDevice.installApp
import org.signal.languagesplittests.LanguageSplitDevice.installedLanguages
import org.signal.languagesplittests.LanguageSplitDevice.isRightToLeft
import org.signal.languagesplittests.LanguageSplitDevice.isShown
import org.signal.languagesplittests.LanguageSplitDevice.log
import org.signal.languagesplittests.LanguageSplitDevice.setChosenLanguage
import org.signal.languagesplittests.LanguageSplitDevice.shell
import org.signal.languagesplittests.LanguageSplitDevice.start
import org.signal.languagesplittests.LanguageSplitDevice.stop
import org.signal.languagesplittests.LanguageSplitDevice.string
import org.signal.languagesplittests.LanguageSplitDevice.updateApp
import org.signal.languagesplittests.LanguageSplitDevice.waitForLog
import org.signal.languagesplittests.LanguageSplitDevice.watchLog
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Language splits installed, added and updated the way Play does it, with the app restarted in between.
 * Each test installs the app afresh. What the app shows is read from the title of the appearance settings screen, which
 * differs between every language here, including the regional variants.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSplitInstallTest {

  companion object {
    private const val TITLE = "preferences__appearance"

    private val TRICKY_LANGUAGES = mapOf(
      "iw" to Locale("iw"), // legacy code < Android 15, "he" after. RTL
      "in" to Locale("in"), // legacy code < Android 15, "id" after
      "yue" to Locale("yue"), // 3 letters, own split separate from zh
      "zh_TW" to Locale("zh", "TW") // region within the zh split, no plain values-zh, HK is a close sibling
    )

    private val RIGHT_TO_LEFT = setOf("iw")
  }

  private val started = TimeSource.Monotonic.markNow()

  @Before
  fun setUp() {
    assertThat(LanguageSplitDevice.apks().any { it.startsWith("base-master") }, name = "Base APK in ${LanguageSplitDevice.apkDir}").isTrue()
  }

  @Test
  fun languageAddedAfterInstallIsAppliedAfterRestart() {
    installApp("en")
    lap("installed")
    assertThat(installedLanguages()).doesNotContain("de")

    setChosenLanguage("de")
    start()
    val english = string(TITLE, Locale.ENGLISH)
    assertThat(isShown(english), name = "English title while de is missing").isTrue()
    lap("started without de")

    addLanguages("de")
    assertThat(installedLanguages()).contains("de")
    lap("added de")

    val german = string(TITLE, Locale.GERMAN)
    assertThat(german, name = "German title from the split").isNotEqualTo(english)
    start()
    assertThat(isShown(german), name = "German title after restart").isTrue()
    lap("restarted in German")
  }

  @Test
  fun trickyLanguageCodesShowTheirOwnSplit() {
    installApp("en", "iw", "in", "yue", "zh")
    assertThat(installedLanguages()).containsAll("iw", "in", "yue", "zh")
    lap("installed")

    val english = string(TITLE, Locale.ENGLISH)
    val titles = TRICKY_LANGUAGES.mapValues { (_, locale) -> string(TITLE, locale) }
    assertThat(titles.values.toSet(), name = "Distinct titles").hasSize(TRICKY_LANGUAGES.size)

    for ((language, title) in titles) {
      assertThat(title, name = language).isNotEqualTo(english)
      setChosenLanguage(language)
      start()
      assertThat(isShown(title), name = "$language title").isTrue()
      assertThat(isRightToLeft(title), name = "$language right to left").isEqualTo(language in RIGHT_TO_LEFT)
      lap("showed $language")
    }
  }

  @Test
  fun updateWithoutChosenLanguageFallsBackAndIsRequested() {
    installApp("en", "de")
    setChosenLanguage("de")
    start()
    assertThat(isShown(string(TITLE, Locale.GERMAN)), name = "German title").isTrue()
    lap("started in German")

    watchLog()
    updateApp("en")
    assertThat(installedLanguages()).doesNotContain("de")
    start()

    assertThat(isShown(string(TITLE, Locale.ENGLISH)), name = "English title after the update").isTrue()
    assertThat(waitForLog("Language de is chosen but not installed. Downloading it"), name = "Startup repair").isTrue()
    lap("updated, fell back and requested de")
  }

  /** Skips where the shell cannot change the device language, which includes API 36 and earlier. */
  @Test
  fun deviceLanguageChangeFollowsTheSplitsOnDisk() {
    assumeTrue("No `cmd locale set-device-locale` on this API level", "set-device-locale" in shell("cmd locale help"))
    val original = shell("cmd locale get-device-locale").trim()

    try {
      installApp("en", "fr")
      setChosenLanguage("zz")
      lap("installed")

      shell("cmd locale set-device-locale fr-FR")
      start()
      val french = string(TITLE, Locale.FRENCH)
      assertThat(isShown(french), name = "French title on a French device").isTrue()
      lap("French device")

      shell("cmd locale set-device-locale de-DE")
      stop()
      start()
      val english = string(TITLE, Locale.ENGLISH)
      assertThat(isShown(english), name = "English title on a German device without de").isTrue()
      assertThat(isShown(french, timeout = Duration.ZERO), name = "French title on a German device").isFalse()
      lap("German device without de")
    } finally {
      shell("cmd locale set-device-locale $original")
    }
  }

  private fun lap(step: String) {
    log("$step after ${started.elapsedNow()}")
  }
}
