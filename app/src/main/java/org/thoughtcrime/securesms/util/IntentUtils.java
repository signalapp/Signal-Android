package org.thoughtcrime.securesms.util;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.function.Consumer;

public class IntentUtils {

  public static boolean isResolvable(@NonNull Context context, @NonNull Intent intent) {
    List<ResolveInfo> resolveInfoList = context.getPackageManager().queryIntentActivities(intent, 0);
    return resolveInfoList != null && resolveInfoList.size() > 1;
  }

  /**
   * From: <a href="https://stackoverflow.com/a/12328282">https://stackoverflow.com/a/12328282</a>
   */
  public static @Nullable LabeledIntent getLabelintent(@NonNull Context context, @NonNull Intent origIntent, int name, int drawable) {
    PackageManager pm         = context.getPackageManager();
    ComponentName  launchName = origIntent.resolveActivity(pm);

    if (launchName != null) {
      Intent resolved = new Intent();
      resolved.setComponent(launchName);
      resolved.setData(origIntent.getData());

      return new LabeledIntent(resolved, context.getPackageName(), name, drawable);
    }
    return null;
  }
}
