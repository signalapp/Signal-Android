package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public class DynamicLanguage {

  private static final String DEFAULT = "zz";

  private Locale currentLocale;

  public void onCreate(Activity activity) {
    currentLocale = getSelectedLocale(activity);
    setActivityLocale(activity, currentLocale);
  }

  public void onResume(Activity activity) {
    if (!currentLocale.getLanguage().equalsIgnoreCase(getSelectedLocale(activity).getLanguage())) {
      Intent intent = activity.getIntent();
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

      activity.startActivity(intent);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
        OverridePendingTransition.invoke(activity);
      }

      activity.finish();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
        OverridePendingTransition.invoke(activity);
      }
    }
  }

  private static void setActivityLocale(Activity activity, Locale selectedLocale) {
    Configuration configuration = activity.getResources().getConfiguration();

    if (!configuration.locale.getLanguage().equalsIgnoreCase(selectedLocale.getLanguage())) {
      configuration.locale = selectedLocale;
      activity.getResources().updateConfiguration(configuration,
                                                  activity.getResources().getDisplayMetrics());
      Locale.setDefault(selectedLocale);
    }
  }

  private static Locale getActivityLocale(Activity activity) {
    return activity.getResources().getConfiguration().locale;
  }

  private static Locale getSelectedLocale(Activity activity) {
    String language = TextSecurePreferences.getLanguage(activity);

    if (language.equals(DEFAULT)) return Locale.getDefault();
    else                          return new Locale(language);
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }

}
