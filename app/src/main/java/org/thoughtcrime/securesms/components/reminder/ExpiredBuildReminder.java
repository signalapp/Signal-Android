package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.PlayStoreUtil;

public class ExpiredBuildReminder extends Reminder {

  public ExpiredBuildReminder(final Context context) {
    super(context.getString(R.string.reminder_header_expired_build),
          context.getString(R.string.reminder_header_expired_build_details));
    setOkListener(v -> PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context));
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible() {
    return SignalStore.misc().isClientDeprecated();
  }

}
