package org.thoughtcrime.securesms.util;

import android.content.Context;
import androidx.annotation.NonNull;

import java.io.IOException;

public class VersionTracker {

  public static int getLastSeenVersion(@NonNull Context context) {
    return TextSecurePreferences.getLastVersionCode(context);
  }

  public static void updateLastSeenVersion(@NonNull Context context) {
    try {
      int currentVersionCode = Util.getCanonicalVersionCode();
      TextSecurePreferences.setLastVersionCode(context, currentVersionCode);
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }
}
