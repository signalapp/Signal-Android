/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import android.app.Application
import androidx.core.os.ConfigurationCompat
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
  fun `resolved locale follows the live default before initialization`() {
    Locale.setDefault(Locale.JAPANESE)

    assertThat(AppLocale.resolvedLocale).isEqualTo(Locale.JAPANESE)
  }

  @Test
  fun `preferred locales lead with the chosen language and keep the device languages`() {
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "de", "fr")) { "de" }

    assertThat(AppLocale.preferredLocales).containsExactly(Locale.GERMAN, Locale.FRENCH)
  }

  @Test
  fun `a device language change keeps the chosen language on the application and the default locale`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    AppLocale.initialize(application, arrayOf("en", "de", "fr", "ja")) { "de" }
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
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "de", "fr")) { language }
    assertThat(AppLocale.resolvedLocale).isEqualTo(Locale.GERMAN)

    language = "en"

    assertThat(AppLocale.resolvedLocale).isEqualTo(Locale.ENGLISH)
  }

  @Test
  fun `preferred locales follow the device when the chosen language is not shipped`() {
    AppLocale.initialize(ApplicationProvider.getApplicationContext(), arrayOf("en", "fr")) { "de" }

    assertThat(AppLocale.preferredLocales).containsExactly(Locale.FRENCH)
  }
}
