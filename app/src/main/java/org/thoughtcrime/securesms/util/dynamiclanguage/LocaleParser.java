package org.thoughtcrime.securesms.util.dynamiclanguage;

import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;

import org.thoughtcrime.securesms.BuildConfig;

import java.util.Arrays;
import java.util.Locale;

final class LocaleParser {

  private LocaleParser() {
  }

  /**
   * Given a language, gets the best choice from the apps list of supported languages and the
   * Systems set of languages.
   */
  static Locale findBestMatchingLocaleForLanguage(@Nullable String language) {
    final Locale locale = LanguageString.parseLocale(language);
    if (appSupportsTheExactLocale(locale)) {
      return locale;
    } else {
      return findBestSystemLocale();
    }
  }

  private static boolean appSupportsTheExactLocale(@Nullable Locale locale) {
    if (locale == null) {
      return false;
    }
    return Arrays.asList(BuildConfig.LANGUAGES).contains(locale.toString());
  }

  /**
   * Get the first preferred language the app supports.
   */
  private static Locale findBestSystemLocale() {
    final Configuration config = Resources.getSystem().getConfiguration();

    final Locale firstMatch = ConfigurationCompat.getLocales(config)
                                                 .getFirstMatch(BuildConfig.LANGUAGES);

    if (firstMatch != null) {
      return firstMatch;
    }

    return Locale.ENGLISH;
  }
}
