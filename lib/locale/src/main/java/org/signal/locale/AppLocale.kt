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

/** Applies the chosen app language. */
object AppLocale {

  @Volatile private var supportedLanguages: Array<String> = emptyArray()

  @Volatile private var storedLanguage: () -> String = { "" }

  /** The language splits on disk when the process started. A split added later is not loaded until the app restarts. Throws before [initialize]. */
  @Volatile
  internal lateinit var installedLanguages: InstalledLanguages
    private set

  /** True when languages can be missing from this install and fetched from Play. */
  @JvmStatic
  val isSplitInstall: Boolean
    get() = installedLanguages.isSplitInstall

  @Volatile private var capturedConfigLocales: LocaleListCompat? = null

  private var cachedChosenLanguage: String? = null
  private var cachedChosenLocale: Locale? = null
  private var cachedConfigLocales: LocaleListCompat? = null
  private var cachedResolvedLocale: Locale? = null

  /** The languages in the configuration Android hands the app, which may lead with a language the app has resources for. */
  private val configLocales: LocaleListCompat
    get() = capturedConfigLocales ?: LocaleListCompat.getDefault()

  /** The device's languages in the order set in system settings. */
  private val deviceLocales: LocaleListCompat
    get() = ConfigurationCompat.getLocales(Resources.getSystem().configuration)

  @JvmStatic
  fun initialize(base: Context, supportedLanguages: Array<String>, languageSplits: Boolean, storedLanguage: () -> String) {
    this.supportedLanguages = supportedLanguages
    this.storedLanguage = storedLanguage
    installedLanguages = LanguageSplitRepository.readInstalledLanguages(base.applicationInfo, languageSplits)
    clearCache()
    updateApplicationLocale(base.resources, config = base.resources.configuration)
  }

  @JvmStatic
  fun onConfigurationChanged(application: Context, newConfig: Configuration) {
    updateApplicationLocale(application.resources, config = newConfig)
  }

  @VisibleForTesting
  internal fun reset() {
    supportedLanguages = emptyArray()
    storedLanguage = { "" }
    capturedConfigLocales = null
    clearCache()
  }

  /**
   * The chosen language if the app ships it and its resources are installed, otherwise the device's best supported
   * language. Parsing and matching are surprisingly expensive and this is called for every timestamp we format, so the
   * result is cached until the language setting or the configuration's locales change.
   */
  @JvmStatic
  val resolvedLocale: Locale
    get() {
      val language = storedLanguage()
      val locales = configLocales
      synchronized(this) {
        if (language != cachedChosenLanguage) {
          cachedChosenLocale = LanguageString.parseLocale(language)
          cachedChosenLanguage = language
          cachedResolvedLocale = null
        }
        if (locales != cachedConfigLocales) {
          cachedConfigLocales = locales
          cachedResolvedLocale = null
        }
        return cachedResolvedLocale ?: LocaleParser.findBestMatchingLocale(cachedChosenLocale, locales, supportedLanguages, installedLanguages).also { cachedResolvedLocale = it }
      }
    }

  /** Whether the app ships a translation for [locale]'s language. */
  internal fun isSupported(locale: Locale): Boolean {
    return supportedLanguages.any { supported ->
      LanguageString.parseLocale(supported)?.let { LanguageString.matches(it.language, locale) } == true
    }
  }

  private fun clearCache() {
    synchronized(this) {
      cachedChosenLanguage = null
      cachedChosenLocale = null
      cachedConfigLocales = null
      cachedResolvedLocale = null
    }
  }

  /**
   * The locale [resolvedLocale] settles on once the chosen language's split is installed. In preference order:
   * - user's chosen language
   * - first system language supported by the app
   * - first system language which would fall back to the base (English)
   */
  @JvmStatic
  val targetLocale: Locale
    get() = LocaleParser.findBestMatchingLocale(LanguageString.parseLocale(storedLanguage()), deviceLocales, supportedLanguages, null)

  /** The resolved language first, then the configuration's languages in order. */
  @JvmStatic
  val preferredLocales: List<Locale>
    get() {
      val locales = configLocales
      return (listOf(resolvedLocale) + (0 until locales.size()).mapNotNull { locales[it] }).distinct()
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
  private fun updateApplicationLocale(resources: Resources, config: Configuration) {
    capturedConfigLocales = ConfigurationCompat.getLocales(config)

    val locale = resolveAndSetDefault()
    if (ConfigurationCompat.getLocales(resources.configuration)[0] == locale) {
      return
    }

    val updated = Configuration(resources.configuration)
    updated.setLocale(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(updated, resources.displayMetrics)
  }
}
