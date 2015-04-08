package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;

public class DynamicLanguage {

  private static final String TAG     = DynamicLanguage.class.getName();
  private static final String DEFAULT = "zz";

  public void onCreate(Activity activity) {
    updateConfiguredLocaleWithUserSelection(activity);
  }

  public void onResume(Activity activity) {
    Locale selectedLocale   = getSelectedLocale(activity);
    Locale configuredLocale = getConfiguredLocale(activity);

    if (!configuredLocale.equals(selectedLocale)) {
      Log.d(TAG, "locale has changed since pause, restarting activity");
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }
  }

  public void updateServiceLocale(Service service) {
    updateConfiguredLocaleWithUserSelection(service);
  }

  public static void onConfigurationChanged(Context context, Configuration changedConfig) {
    Locale selectedLocale   = getSelectedLocale(context);
    Locale configuredLocale = changedConfig.locale;

    if (!configuredLocale.equals(selectedLocale)) {
      Log.d(TAG, "overriding locale change from " + configuredLocale + " to " + selectedLocale);
      Configuration fixedConfig        = new Configuration(changedConfig);
                    fixedConfig.locale = selectedLocale;

      context.getResources().updateConfiguration(fixedConfig,
                                                 context.getResources().getDisplayMetrics());
    }
  }

  private static void updateConfiguredLocaleWithUserSelection(Context context) {
    Locale selectedLocale   = getSelectedLocale(context);
    Locale configuredLocale = getConfiguredLocale(context);

    if (!configuredLocale.equals(selectedLocale)) {
      Log.d(TAG, "updating config to use " + selectedLocale + " locale");

      Configuration newConfig        = new Configuration(context.getResources().getConfiguration());
                    newConfig.locale = selectedLocale;

      context.getResources().updateConfiguration(newConfig,
                                                 context.getResources().getDisplayMetrics());
    }
  }

  private static Locale getConfiguredLocale(Context context) {
    return context.getResources().getConfiguration().locale;
  }

  private static Locale getSelectedLocale(Context context) {
    String language[] = TextUtils.split(TextSecurePreferences.getLanguage(context), "_");

    if (language[0].equals(DEFAULT)) {
      return Locale.getDefault();
    } else if (language.length == 2) {
      return new Locale(language[0], language[1]);
    } else {
      return new Locale(language[0]);
    }
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }

}
