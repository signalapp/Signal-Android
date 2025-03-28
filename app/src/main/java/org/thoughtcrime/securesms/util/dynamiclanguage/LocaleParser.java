package org.thoughtcrime.securesms.util.dynamiclanguage;

import android.content.res.Resources;
import android.os.Build;

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
  static @NonNull Locale findBestMatchingLocaleForLanguage(@Nullable String language) {
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
  private static @NonNull Locale findBestSystemLocale() {
    LocaleListCompat localeList;
    if (Build.VERSION.SDK_INT < 24) {
      localeList = LocaleListCompat.create(Resources.getSystem().getConfiguration().locale);
    } else {
      localeList = LocaleListCompat.getAdjustedDefault();
    }

    final Locale firstMatch = localeList.getFirstMatch(BuildConfig.LANGUAGES);

    if (firstMatch != null) {
      return firstMatch;
    }

    return Locale.ENGLISH;
  }
}
