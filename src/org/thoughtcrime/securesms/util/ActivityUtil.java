package org.thoughtcrime.securesms.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;

/**
 * Class used to perform actions
 * on Activities in a compatible way
 *
 * @author fercarcedo
 */
public class ActivityUtil {
  public static void recreateActivity(Activity activity) {
    if (activity != null) {
      Intent intent = activity.getIntent();
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
      activity.finish();
      activity.overridePendingTransition(0, 0);
      activity.startActivity(intent);
      activity.overridePendingTransition(0, 0);
    }
  }
}
