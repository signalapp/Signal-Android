package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.content.res.Resources;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public class OutdatedBuildReminder extends ExpiredBuildReminder {

  public OutdatedBuildReminder(final Context context) {
    super(context);

    Resources res = context.getResources();
    this.setText(res.getString(R.string.reminder_header_outdated_build));
    int days = Util.getDaysTillBuildExpiry();
    this.setTitle(res.getQuantityString(R.plurals.reminder_header_outdated_build_details, days, days));
  }

  public static boolean isEligible() {
    return Util.getDaysTillBuildExpiry() <= 10;
  }

}
