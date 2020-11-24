package org.thoughtcrime.securesms.util.dynamiclanguage;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Updates a context with an alternative language.
 */
public final class DynamicLanguageContextWrapper {

  public static Context updateContext(Context context, String language) {
    final Locale newLocale = LocaleParser.findBestMatchingLocaleForLanguage(language);

    Locale.setDefault(newLocale);

    final Resources     resources = context.getResources();
    final Configuration config    = resources.getConfiguration();
    final Configuration newConfig = copyWithNewLocale(config, newLocale);

    resources.updateConfiguration(newConfig, resources.getDisplayMetrics());

    return context;
  }

  private static Configuration copyWithNewLocale(Configuration config, Locale locale) {
    final Configuration copy = new Configuration(config);
    copy.setLocale(locale);
    return copy;
  }

}
