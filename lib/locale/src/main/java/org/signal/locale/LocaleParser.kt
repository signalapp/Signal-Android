package org.signal.locale

import androidx.core.os.LocaleListCompat
import java.util.Locale

internal object LocaleParser {

  /**
   * Given a chosen locale, gets the best choice from the apps list of supported languages and the
   * Systems set of languages. A supported language whose resources are not installed yet falls
   * back to the system choice, so dates and strings agree until Play delivers it. A null
   * installedLanguages skips that check.
   */
  @JvmStatic
  fun findBestMatchingLocale(
    locale: Locale?,
    systemLocaleList: LocaleListCompat,
    supportedLanguages: Array<String>,
    installedLanguages: InstalledLanguages?
  ): Locale {
    return if (locale != null && supportedLanguages.contains(locale.toString()) && (installedLanguages == null || installedLanguages.contains(locale))) {
      locale
    } else {
      systemLocaleList.getFirstMatch(supportedLanguages) ?: Locale.ENGLISH
    }
  }
}
