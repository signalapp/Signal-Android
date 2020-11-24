package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

public class IntentUtils {

  public static boolean isResolvable(@NonNull Context context, @NonNull Intent intent) {
    return context.getPackageManager()
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
  }

}
