package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.IOException;

public class VersionTracker {


  public static int getLastSeenVersion(Context context) {
    return TextSecurePreferences.getLastVersionCode(context);
  }

  public static void updateLastSeenVersion(Context context) {
    try {
      int currentVersionCode = Util.getCurrentApkReleaseVersion(context);
      TextSecurePreferences.setLastVersionCode(context, currentVersionCode);
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }
}
