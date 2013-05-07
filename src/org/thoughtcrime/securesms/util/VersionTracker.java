package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

public class VersionTracker {

  private static final String LAST_VERSION_CODE = "last_version_code";

  public static int getLastSeenVersion(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    return preferences.getInt(LAST_VERSION_CODE, 0);
  }

  public static void updateLastSeenVersion(Context context) {
    try {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      int currentVersionCode        = context.getPackageManager()
                                             .getPackageInfo(context.getPackageName(), 0)
                                             .versionCode;
      preferences.edit().putInt(LAST_VERSION_CODE, currentVersionCode).commit();
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }
}
