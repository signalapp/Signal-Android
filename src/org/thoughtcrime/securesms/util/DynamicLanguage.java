package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.app.Service;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
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
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        activity.recreate();
      } else {
        Intent intent = activity.getIntent();
        activity.finish();
        OverridePendingTransition.invoke(activity);
        activity.startActivity(intent);
        OverridePendingTransition.invoke(activity);
      }
    }
  }

  public void setServiceLocale(Service service)
  {
    currentLocale = getSelectedLocale(service);
    setContextLocale(service, currentLocale);
  }

  private static void setContextLocale(ContextWrapper context, Locale selectedLocale) {
    Configuration configuration = context.getResources().getConfiguration();

    if (!configuration.locale.equals(selectedLocale)) {
      configuration.locale = selectedLocale;
      context.getResources().updateConfiguration(configuration,
          context.getResources().getDisplayMetrics());
    }
  }

  private static Locale getContextLocale(ContextWrapper context) {
    return context.getResources().getConfiguration().locale;
  }

  private static Locale getSelectedLocale(ContextWrapper context) {
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
