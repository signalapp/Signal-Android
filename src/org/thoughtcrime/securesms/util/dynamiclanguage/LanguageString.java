package org.thoughtcrime.securesms.util.dynamiclanguage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class LanguageString {

  private LanguageString() {
  }

  /**
   * @param languageString String in format language_REGION, e.g. en_US
   * @return Locale, or null if cannot parse
   */
  @Nullable
  public static Locale parseLocale(@Nullable String languageString) {
    if (languageString == null || languageString.isEmpty()) {
      return null;
    }

    final Locale locale = createLocale(languageString);

    if (!isValid(locale)) {
      return null;
    } else {
      return locale;
    }
  }

  private static Locale createLocale(@NonNull String languageString) {
    final String language[] = languageString.split("_");
    if (language.length == 2) {
      return new Locale(language[0], language[1]);
    } else {
      return new Locale(language[0]);
    }
  }

  private static boolean isValid(@NonNull Locale locale) {
    try {
      return locale.getISO3Language() != null && locale.getISO3Country() != null;
    } catch (Exception ex) {
      return false;
    }
  }
}
