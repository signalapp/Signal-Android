package org.thoughtcrime.securesms.components;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public class ExpiredBuildReminder extends Reminder {

  public ExpiredBuildReminder() {
    super(R.drawable.ic_warning_dark,
          R.string.reminder_header_expired_build,
          R.string.reminder_header_expired_build_details);
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible() {
    return !Util.isBuildFresh();
  }

}
