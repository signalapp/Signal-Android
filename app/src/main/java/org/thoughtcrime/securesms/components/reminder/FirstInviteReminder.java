package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;

public final class FirstInviteReminder extends Reminder {

  private final int percentIncrease;

  public FirstInviteReminder(final int percentIncrease) {
    super(R.string.FirstInviteReminder__title, NO_RESOURCE);
    this.percentIncrease = percentIncrease;

    addAction(new Action(R.string.InsightsReminder__invite, R.id.reminder_action_invite));
    addAction(new Action(R.string.InsightsReminder__view_insights, R.id.reminder_action_view_insights));
  }

  @Override
  public @NonNull CharSequence getText(@NonNull Context context) {
    return context.getString(R.string.FirstInviteReminder__description, percentIncrease);
  }
}
