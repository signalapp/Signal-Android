package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.concurrent.TimeUnit;

public class OutdatedBuildReminder extends Reminder {

  public OutdatedBuildReminder(final Context context) {
    super(context.getString(R.string.reminder_header_outdated_build),
          getPluralsText(context));
    setOkListener(v -> PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context));
  }

  private static CharSequence getPluralsText(final Context context) {
    int days = getDaysUntilExpiry() - 1;
    if (days == 0) {
      return context.getString(R.string.reminder_header_outdated_build_details_today);
    }
    return context.getResources().getQuantityString(R.plurals.reminder_header_outdated_build_details, days, days);
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible() {
    return getDaysUntilExpiry() <= 10;
  }

  private static int getDaysUntilExpiry() {
    return (int) TimeUnit.MILLISECONDS.toDays(Util.getTimeUntilBuildExpiry());
  }
}
