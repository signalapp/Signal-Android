/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Applies the chosen app language, or the device language until the app initializes it. */
object AppLocale {

  @Volatile private var supportedLanguages: Array<String> = emptyArray()

  @Volatile private var storedLanguage: () -> String = { "" }

  @Volatile private var capturedDeviceLocales: LocaleListCompat? = null

  private var cachedChosenLanguage: String? = null
  private var cachedChosenLocale: Locale? = null
  private var cachedDeviceLocales: LocaleListCompat? = null
  private var cachedResolvedLocale: Locale? = null

  private val deviceLocales: LocaleListCompat
    get() = capturedDeviceLocales ?: LocaleListCompat.getDefault()

  @JvmStatic
  fun initialize(base: Context, supportedLanguages: Array<String>, storedLanguage: () -> String) {
    this.supportedLanguages = supportedLanguages
    this.storedLanguage = storedLanguage
    clearCache()
    updateApplicationLocale(base.resources, deviceConfig = base.resources.configuration)
  }

  @JvmStatic
  fun onConfigurationChanged(application: Context, newConfig: Configuration) {
    updateApplicationLocale(application.resources, deviceConfig = newConfig)
  }

  @VisibleForTesting
  internal fun reset() {
    supportedLanguages = emptyArray()
    storedLanguage = { "" }
    capturedDeviceLocales = null
    clearCache()
  }

  /**
   * The chosen language if the app ships it, otherwise the device's best supported language. Parsing and matching are
   * surprisingly expensive and this is called for every timestamp we format, so the result is cached until the language
   * setting or the device locales change.
   */
  @JvmStatic
  val resolvedLocale: Locale
    get() {
      val language = storedLanguage()
      val device = deviceLocales
      synchronized(this) {
        if (language != cachedChosenLanguage) {
          cachedChosenLocale = LanguageString.parseLocale(language)
          cachedChosenLanguage = language
          cachedResolvedLocale = null
        }
        if (device != cachedDeviceLocales) {
          cachedDeviceLocales = device
          cachedResolvedLocale = null
        }
        return cachedResolvedLocale ?: LocaleParser.findBestMatchingLocale(cachedChosenLocale, device, supportedLanguages).also { cachedResolvedLocale = it }
      }
    }

  private fun clearCache() {
    synchronized(this) {
      cachedChosenLanguage = null
      cachedChosenLocale = null
      cachedDeviceLocales = null
      cachedResolvedLocale = null
    }
  }

  /** The resolved language first, then the device's languages in order. */
  @JvmStatic
  val preferredLocales: List<Locale>
    get() {
      val device = deviceLocales
      return (listOf(resolvedLocale) + (0 until device.size()).mapNotNull { device[it] }).distinct()
    }

  /** Rewrites the locale of [config] to the chosen language and makes it the JVM default. */
  @JvmStatic
  fun prepareOverrideConfiguration(config: Configuration) {
    config.setLocale(resolveAndSetDefault())
  }

  /** An override carrying only the chosen locale, so later orientation, size and font scale changes still reach the activity. */
  @JvmStatic
  fun createOverrideConfiguration(): Configuration {
    return Configuration().apply {
      fontScale = 0f
      prepareOverrideConfiguration(this)
    }
  }

  private fun resolveAndSetDefault(): Locale = resolvedLocale.also(Locale::setDefault)

  /**
   * Resolves the locale, makes it the JVM default, and updates the application's shared [Resources] so holders of the
   * application context and services see it. The deprecated call is the only way to reach them < Android 13.
   */
  private fun updateApplicationLocale(resources: Resources, deviceConfig: Configuration) {
    capturedDeviceLocales = ConfigurationCompat.getLocales(deviceConfig)

    val locale = resolveAndSetDefault()
    if (ConfigurationCompat.getLocales(resources.configuration)[0] == locale) {
      return
    }

    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
  }
}
