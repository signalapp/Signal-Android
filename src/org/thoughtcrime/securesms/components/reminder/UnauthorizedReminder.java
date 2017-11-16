package org.thoughtcrime.securesms.components.reminder;


import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.RegistrationActivity;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class UnauthorizedReminder extends Reminder {

  public UnauthorizedReminder(final Context context) {
    super("Device no longer registered",
          "This is likely because you registered your phone number with Signal on a different device. Tap to re-register.");

    setOkListener(v -> context.startActivity(new Intent(context, RegistrationActivity.class)));
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible(Context context) {
    return TextSecurePreferences.isUnauthorizedRecieved(context);
  }
}
