package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  private int currentTheme;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

      activity.finish();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
        OverridePendingTransition.invoke(activity);
      }

      activity.startActivity(intent);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
        OverridePendingTransition.invoke(activity);
      }

    }
  }

  private static int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals("light")) {
      if (activity instanceof ConversationListActivity) return R.style.TextSecure_LightTheme_NavigationDrawer;
      else if (activity instanceof ConversationActivity) return R.style.TextSecure_LightTheme_ConversationActivity;
      else                                              return R.style.TextSecure_LightTheme;
    } else if (theme.equals("dark")) {
      if (activity instanceof ConversationListActivity) return R.style.TextSecure_DarkTheme_NavigationDrawer;
      else if (activity instanceof ConversationActivity) return R.style.TextSecure_DarkTheme_ConversationActivity;
      else                                              return R.style.TextSecure_DarkTheme;
    }

    return R.style.TextSecure_LightTheme;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
