package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.R;

public class UnsupportedAndroidVersionReminder extends Reminder {

  public UnsupportedAndroidVersionReminder(@NonNull Context context) {
    super(null, context.getString(R.string.reminder_header_the_latest_signal_features_wont_work));
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible() {
    return Build.VERSION.SDK_INT < 19;
  }
}
