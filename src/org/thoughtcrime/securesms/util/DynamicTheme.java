package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
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

  public static int getDrawableAttachPicture(Context context) {
    String theme = getThemeString(context);
    if (theme.equals("dark")) {
      return R.drawable.ic_attach_picture_holo_dark;
    }
    return R.drawable.ic_attach_picture_holo_light;
  }

  public static int getDrawableAttachVideo(Context context) {
    String theme = getThemeString(context);
    if (theme.equals("dark")) {
      return R.drawable.ic_attach_video_holo_dark;
    }
    return R.drawable.ic_attach_video_holo_light;
  }

  public static int getDrawableAttachAudio(Context context) {
    String theme = getThemeString(context);
    if (theme.equals("dark")) {
      return R.drawable.ic_attach_audio_holo_dark;
    }
    return R.drawable.ic_attach_audio_holo_light;
  }

  private static int getSelectedTheme(Activity activity) {
    String theme = getThemeString(activity);
    if (theme.equals("light")) {
      if (activity instanceof ConversationListActivity) return R.style.TextSecure_LightTheme_NavigationDrawer;
      else                                              return R.style.TextSecure_LightTheme;
    } else if (theme.equals("dark")) {
      if (activity instanceof ConversationListActivity) return R.style.TextSecure_DarkTheme_NavigationDrawer;
      else                                              return R.style.TextSecure_DarkTheme;
    }

    return R.style.TextSecure_LightTheme;
  }

  private static String getThemeString(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(ApplicationPreferencesActivity.THEME_PREF, "light");
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
