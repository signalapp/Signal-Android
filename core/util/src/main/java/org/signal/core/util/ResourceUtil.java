package org.signal.core.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Locale;

/**
 * Gives access to English strings.
 */
public final class ResourceUtil {

  private ResourceUtil() {
  }

  public static Resources getEnglishResources(@NonNull Context context) {
    return getResources(context, Locale.ENGLISH);
  }

  public static Resources getResources(@NonNull Context context, @NonNull Locale locale) {
    Configuration configurationLocal = context.getResources().getConfiguration();

    Configuration configurationEn = new Configuration(configurationLocal);
    configurationEn.setLocale(locale);

    return context.createConfigurationContext(configurationEn)
                  .getResources();
  }
}
