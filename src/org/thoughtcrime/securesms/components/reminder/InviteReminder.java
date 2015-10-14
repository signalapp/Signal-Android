package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

public class InviteReminder extends Reminder {

  public InviteReminder(final @NonNull Context context,
                        final @NonNull Recipients recipients)
  {
    super(context.getString(R.string.reminder_header_invite_title),
          context.getString(R.string.reminder_header_invite_text, recipients.toShortString()),
          context.getString(R.string.reminder_header_invite_button));

    setDismissListener(new OnClickListener() {
      @Override public void onClick(View v) {
        new AsyncTask<Void,Void,Void>() {

          @Override protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(context).setSeenInviteReminder(recipients, true);
            return null;
          }
        }.execute();
      }
    });
  }
}
