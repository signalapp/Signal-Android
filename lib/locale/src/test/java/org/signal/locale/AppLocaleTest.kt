/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import android.app.Application
import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, qualifiers = "fr")
class AppLocaleTest {

  @After
  fun tearDown() {
    AppLocale.reset()
  }

  @Test
  fun `isSupported matches by language, including legacy codes`() {
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "pt_BR", "iw"), languageSplits = true) { "zz" }

    assertThat(AppLocale.isSupported(Locale("pt", "PT"))).isTrue()
    assertThat(AppLocale.isSupported(Locale("he", "IL"))).isTrue()
    assertThat(AppLocale.isSupported(Locale.GERMAN)).isFalse()
  }

  @Test
  fun `the splits on disk are captured when the app initializes`() {
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "de"), languageSplits = true) { "zz" }

    assertThat(AppLocale.installedLanguages.isSplitInstall).isFalse()
  }

  @Test
  fun `target locale keeps the chosen language while its split is missing`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    application.applicationInfo.splitSourceDirs = arrayOf("/data/app/split_config.fr.apk")
    AppLocale.initialize(application, arrayOf("en", "de", "fr"), languageSplits = true) { "de" }

    assertThat(AppLocale.resolvedLocale).isEqualTo(Locale.FRENCH)
    assertThat(AppLocale.targetLocale).isEqualTo(Locale.GERMAN)
  }

  @Test
  fun `target locale follows the system's order when the app's configuration leads with an installed language`() {
    RuntimeEnvironment.setQualifiers("pt-rBR")
    val application = ApplicationProvider.getApplicationContext<Application>()
    val adjusted = Configuration(application.resources.configuration).also { ConfigurationCompat.setLocales(it, LocaleListCompat.create(Locale.US, Locale("pt", "BR"))) }
    @Suppress("DEPRECATION")
    application.resources.updateConfiguration(adjusted, application.resources.displayMetrics)
    AppLocale.initialize(application, arrayOf("en", "pt", "pt_BR"), languageSplits = true) { "zz" }

    assertThat(AppLocale.resolvedLocale).isEqualTo(Locale.US)
    assertThat(AppLocale.targetLocale).isEqualTo(Locale("pt", "BR"))
  }

  @Test
  fun `preferred locales lead with the chosen language and keep the device languages`() {
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "de", "fr"), languageSplits = true) { "de" }

    assertThat(AppLocale.preferredLocales).containsExactly(Locale.GERMAN, Locale.FRENCH)
  }

  @Test
  fun `a device language change keeps the chosen language on the application and the default locale`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    AppLocale.initialize(application, arrayOf("en", "de", "fr", "ja"), languageSplits = true) { "de" }
    RuntimeEnvironment.setQualifiers("ja")
    assertThat(ConfigurationCompat.getLocales(application.resources.configuration)[0]).isEqualTo(Locale.JAPANESE)
    assertThat(Locale.getDefault()).isEqualTo(Locale.JAPANESE)

    AppLocale.onConfigurationChanged(application, application.resources.configuration)

    assertThat(AppLocale.preferredLocales).containsExactly(Locale.GERMAN, Locale.JAPANESE)
    assertThat(Locale.getDefault()).isEqualTo(Locale.GERMAN)
    assertThat(ConfigurationCompat.getLocales(application.resources.configuration)[0]).isEqualTo(Locale.GERMAN)
  }

  @Test
  fun `resolved locale follows a change to the stored language`() {
    var language = "de"
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "de", "fr"), languageSplits = true) { language }
    assertThat(AppLocale.resolvedLocale).isEqualTo(Locale.GERMAN)

    language = "en"

    assertThat(AppLocale.resolvedLocale).isEqualTo(Locale.ENGLISH)
  }

  @Test
  fun `preferred locales follow the device when the chosen language is not shipped`() {
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "fr"), languageSplits = true) { "de" }

    assertThat(AppLocale.preferredLocales).containsExactly(Locale.FRENCH)
  }
}
