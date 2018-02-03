package org.thoughtcrime.securesms.components.reminder;


import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RegistrationActivity;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class UnauthorizedReminder extends Reminder {

  public UnauthorizedReminder(final Context context) {
    super(context.getString(R.string.UnauthorizedReminder_device_no_longer_registered),
          context.getString(R.string.UnauthorizedReminder_this_is_likely_because_you_registered_your_phone_number_with_Signal_on_a_different_device));

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
