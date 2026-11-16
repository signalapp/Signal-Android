package org.signal.locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.LocaleListCompat;

import java.util.Arrays;
import java.util.Locale;

final class LocaleParser {

  private LocaleParser() {
  }

  /**
   * Given a chosen locale, gets the best choice from the apps list of supported languages and the
   * Systems set of languages.
   */
  static @NonNull Locale findBestMatchingLocale(@Nullable Locale locale,
                                                @NonNull LocaleListCompat systemLocaleList,
                                                @NonNull String[] supportedLanguages)
  {
    if (locale != null && Arrays.asList(supportedLanguages).contains(locale.toString())) {
      return locale;
    } else {
      final Locale firstMatch = systemLocaleList.getFirstMatch(supportedLanguages);
      return firstMatch != null ? firstMatch : Locale.ENGLISH;
    }
  }
}
