package org.thoughtcrime.securesms.util.dynamiclanguage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.LocaleListCompat;

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
  static @NonNull Locale findBestMatchingLocaleForLanguage(@Nullable String language, @NonNull LocaleListCompat systemLocaleList) {
    final Locale locale = LanguageString.parseLocale(language);
    if (appSupportsTheExactLocale(locale)) {
      return locale;
    } else {
      final Locale firstMatch = systemLocaleList.getFirstMatch(BuildConfig.LANGUAGES);
      return firstMatch != null ? firstMatch : Locale.ENGLISH;
    }
  }

  private static boolean appSupportsTheExactLocale(@Nullable Locale locale) {
    if (locale == null) {
      return false;
    }
    return Arrays.asList(BuildConfig.LANGUAGES).contains(locale.toString());
  }
}
