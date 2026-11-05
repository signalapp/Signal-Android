package org.thoughtcrime.securesms.util

import androidx.core.os.LocaleListCompat
import org.signal.storage.keyvalue.PlainTextKeyValueStore
import org.thoughtcrime.securesms.util.dynamiclanguage.LanguageString
import java.util.Locale

object LocaleUtil {

  private var cachedLanguage: String? = null
  private var cachedSignalLocale: Locale? = null

  fun getFirstLocale(): Locale {
    return getSignalLocale() ?: LocaleListCompat.getDefault().get(0) ?: Locale.getDefault()
  }

  /**
   * Get a user priority list of locales supported on the device, with the locale set via Signal settings
   * as highest priority over system settings.
   */
  fun getLocaleDefaults(): List<Locale> {
    val locales: MutableList<Locale> = mutableListOf()
    val signalLocale: Locale? = getSignalLocale()
    val localeList: LocaleListCompat = LocaleListCompat.getDefault()

    if (signalLocale != null) {
      locales += signalLocale
    }

    for (index in 0 until localeList.size()) {
      locales += localeList.get(index) ?: continue
    }

    return locales
  }

  /** Parsing the language setting is surprisingly expensive and this is called for every timestamp we format. */
  private fun getSignalLocale(): Locale? {
    val language = PlainTextKeyValueStore.language
    synchronized(this) {
      if (language != cachedLanguage) {
        cachedSignalLocale = LanguageString.parseLocale(language)
        cachedLanguage = language
      }
      return cachedSignalLocale
    }
  }
}
