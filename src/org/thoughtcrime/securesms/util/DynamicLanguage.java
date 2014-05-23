package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.text.TextUtils;

import java.util.Locale;

public class DynamicLanguage {

  private static final String DEFAULT = "zz";

  private Locale currentLocale;

  public void onCreate(Activity activity) {
    currentLocale = getSelectedLocale(activity);
    setActivityLocale(activity, currentLocale);
  }

  public void onResume(Activity activity) {
    if (!currentLocale.equals(getSelectedLocale(activity))) {
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }
  }

  private static void setActivityLocale(Activity activity, Locale selectedLocale) {
    Configuration configuration = activity.getResources().getConfiguration();

    if (!configuration.locale.equals(selectedLocale)) {
      configuration.locale = selectedLocale;
      activity.getResources().updateConfiguration(configuration,
                                                  activity.getResources().getDisplayMetrics());
    }
  }

  private static Locale getActivityLocale(Activity activity) {
    return activity.getResources().getConfiguration().locale;
  }

  private static Locale getSelectedLocale(Activity activity) {
    String language[] = TextUtils.split(TextSecurePreferences.getLanguage(activity), "_");

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
