package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;

import org.thoughtcrime.securesms.InviteActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class ShareReminder extends Reminder {

  public ShareReminder(final @NonNull Context context) {
    super(context.getString(R.string.reminder_header_share_title),
          context.getString(R.string.reminder_header_share_text),
          context.getString(R.string.reminder_header_share_button));

    setDismissListener(new OnClickListener() {
      @Override public void onClick(View v) {
        TextSecurePreferences.setPromptedShare(context, true);
      }
    });

    setOkListener(new OnClickListener() {
      @Override public void onClick(View v) {
        TextSecurePreferences.setPromptedShare(context, true);
        context.startActivity(new Intent(context, InviteActivity.class));
      }
    });
  }

  public static boolean isEligible(final @NonNull Context context) {
    return !TextSecurePreferences.hasPromptedShare(context);
  }
}
