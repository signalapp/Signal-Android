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
public final class EnglishResourceUtil {

  private EnglishResourceUtil() {
  }

  public static Resources getEnglishResources(@NonNull Context context) {
    Configuration configurationLocal = context.getResources().getConfiguration();

    Configuration configurationEn = new Configuration(configurationLocal);
    configurationEn.setLocale(Locale.ENGLISH);

    return context.createConfigurationContext(configurationEn)
                  .getResources();
  }
}
