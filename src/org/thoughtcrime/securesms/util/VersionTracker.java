package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

public class VersionTracker {

  public static int getLastSeenVersion(Context context) {
    return TextSecurePreferences.getLastVersionCode(context);
  }

  public static void updateLastSeenVersion(Context context) {
    try {
      int currentVersionCode        = context.getPackageManager()
                                             .getPackageInfo(context.getPackageName(), 0)
                                             .versionCode;
      TextSecurePreferences.setLastVersionCode(context, currentVersionCode);
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }
}
