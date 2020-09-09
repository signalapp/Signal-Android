package org.thoughtcrime.securesms.util;

import android.content.Context;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;

import java.io.IOException;

public class VersionTracker {

  private static final String TAG = Log.tag(VersionTracker.class);

  public static int getLastSeenVersion(@NonNull Context context) {
    return TextSecurePreferences.getLastVersionCode(context);
  }

  public static void updateLastSeenVersion(@NonNull Context context) {
    try {
      int currentVersionCode = Util.getCanonicalVersionCode();
      int lastVersionCode    = TextSecurePreferences.getLastVersionCode(context);

      if (currentVersionCode != lastVersionCode) {
        Log.i(TAG, "Upgraded from " + lastVersionCode + " to " + currentVersionCode);
        SignalStore.misc().clearClientDeprecated();
        TextSecurePreferences.setLastVersionCode(context, currentVersionCode);
      }
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }
}
