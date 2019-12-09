package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

public final class SecondInviteReminder extends Reminder {

  private final int progress;

  public SecondInviteReminder(final @NonNull Context context,
                              final @NonNull Recipient recipient,
                              final int percent)
  {
    super(context.getString(R.string.SecondInviteReminder__title),
          context.getString(R.string.SecondInviteReminder__description, recipient.getDisplayName(context)));

    this.progress  = percent;

    addAction(new Action(context.getString(R.string.InsightsReminder__invite), R.id.reminder_action_invite));
    addAction(new Action(context.getString(R.string.InsightsReminder__view_insights), R.id.reminder_action_view_insights));
  }

  @Override
  public int getProgress() {
    return progress;
  }
}
