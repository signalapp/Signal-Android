package org.thoughtcrime.securesms.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;

import java.util.Locale;

public class DynamicLanguage {

  private static final String DEFAULT = "zz";

  private Locale currentLocale;

  public void onCreate(Activity activity) {
    currentLocale = getSelectedLocale(activity);
    setContextLocale(activity, currentLocale);
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

  public void updateServiceLocale(Service service) {
    currentLocale = getSelectedLocale(service);
    setContextLocale(service, currentLocale);
  }

  public Locale getCurrentLocale() {
    return currentLocale;
  }

  @RequiresApi(VERSION_CODES.JELLY_BEAN_MR1)
  public static int getLayoutDirection(Context context) {
    Configuration configuration = context.getResources().getConfiguration();
    return configuration.getLayoutDirection();
  }

  private static void setContextLocale(Context context, Locale selectedLocale) {
    Configuration configuration = context.getResources().getConfiguration();

    if (!configuration.locale.equals(selectedLocale)) {
      configuration.locale = selectedLocale;
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        configuration.setLayoutDirection(selectedLocale);
      }
      context.getResources().updateConfiguration(configuration,
                                                 context.getResources().getDisplayMetrics());
    }
  }

  private static Locale getActivityLocale(Activity activity) {
    return activity.getResources().getConfiguration().locale;
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
