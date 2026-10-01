package org.thoughtcrime.securesms.util.dynamiclanguage;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.os.LocaleListCompat;

import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore;

import java.util.Locale;

/**
 * Updates a context with an alternative language.
 */
public final class DynamicLanguageContextWrapper {
  private DynamicLanguageContextWrapper() {}

  private static LocaleListCompat systemLocaleList = LocaleListCompat.getEmptyLocaleList();

  public static void prepareOverrideConfiguration(@NonNull Configuration base) {
    if (Build.VERSION.SDK_INT >= 24) {
      systemLocaleList = LocaleListCompat.wrap(base.getLocales());
    } else {
      systemLocaleList = LocaleListCompat.create(base.locale);
    }
    Locale newLocale = getUsersSelectedLocale();

    Locale.setDefault(newLocale);
    base.setLocale(newLocale);
  }

  @SuppressWarnings("deprecated")
  public static @NonNull Locale getUsersSelectedLocale() {
    String language = PlainTextKeyValueStore.getLanguage();
    return LocaleParser.findBestMatchingLocaleForLanguage(language, systemLocaleList);
  }

  public static void updateContext(@NonNull Context base) {
    Configuration config = base.getResources().getConfiguration();

    prepareOverrideConfiguration(config);
  }
}
