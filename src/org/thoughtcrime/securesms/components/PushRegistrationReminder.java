package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RegistrationActivity;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;

public class PushRegistrationReminder extends Reminder {
  public static final long REMINDER_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000;

  public PushRegistrationReminder(final Context context, final MasterSecret masterSecret) {
    super(R.drawable.ic_push_registration_reminder,
          R.string.reminder_header_push_title,
          R.string.reminder_header_push_text);

    final OnClickListener okListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(context, RegistrationActivity.class);
        intent.putExtra("master_secret", masterSecret);
        context.startActivity(intent);
      }
    };
    final OnClickListener cancelListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setLastPushReminderTime(context, System.currentTimeMillis());
      }
    };
    setOkListener(okListener);
    setCancelListener(cancelListener);
  }

  public static boolean isEligible(Context context) {
    return !TextSecurePreferences.isPushRegistered(context) &&
        (TextSecurePreferences.getLastPushReminderTime(context) + REMINDER_INTERVAL_MS < System.currentTimeMillis());
  }
}
