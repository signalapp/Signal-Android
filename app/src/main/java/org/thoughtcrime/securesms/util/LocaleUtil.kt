package org.thoughtcrime.securesms.util

import androidx.core.os.LocaleListCompat
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.dynamiclanguage.LanguageString
import java.util.Locale

object LocaleUtil {

  fun getFirstLocale(): Locale {
    return getLocaleDefaults().firstOrNull() ?: Locale.getDefault()
  }

  /**
   * Get a user priority list of locales supported on the device, with the locale set via Signal settings
   * as highest priority over system settings.
   */
  fun getLocaleDefaults(): List<Locale> {
    val locales: MutableList<Locale> = mutableListOf()
    val signalLocale: Locale? = LanguageString.parseLocale(SignalStore.settings.language)
    val localeList: LocaleListCompat = LocaleListCompat.getDefault()

    if (signalLocale != null) {
      locales += signalLocale
    }

    for (index in 0 until localeList.size()) {
      locales += localeList.get(index) ?: continue
    }

    return locales
  }
}
