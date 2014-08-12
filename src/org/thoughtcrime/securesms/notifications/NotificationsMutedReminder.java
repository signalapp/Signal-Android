package org.thoughtcrime.securesms.notifications;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.Reminder;
import org.thoughtcrime.securesms.components.ReminderView;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientNotificationsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Reminder that a certain thread has been muted.
 *
 * @author Lukas Barth
 */
public class NotificationsMutedReminder extends Reminder {
  private Recipient recipient;
  private Context context;

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public NotificationsMutedReminder(final Context context, final Recipient recipient, final ReminderView reminderView) {
    super(R.drawable.ic_smiles_bell,
            null,
            R.string.reminder_header_silenced_text);

    this.context = context;

    final View.OnClickListener okListener = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        RecipientNotificationsDatabase notificationsDatabase = DatabaseFactory.getNotificationDatabase(context);
        notificationsDatabase.setSilenceUntil(recipient, null);
        notificationsDatabase.setSilencePermanently(recipient, false);

        reminderView.hide();
      }
    };

    setOkListener(okListener);
  }

  @Override
  public String getOKText() {
    return this.context.getResources().getString(R.string.reminder_header_silenced_ok);
  }

  @Override
  public Integer getCancelResId() {
    return null;
  }
}
