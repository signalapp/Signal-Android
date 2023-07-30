package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;

/**
 * Shown to admins when there are pending group join requests.
 */
public final class PendingGroupJoinRequestsReminder extends Reminder {

  private final int count;

  public PendingGroupJoinRequestsReminder(int count) {
    this.count = count;

    addAction(new Action(R.string.PendingGroupJoinRequestsReminder_view, R.id.reminder_action_review_join_requests));
  }

  @Override
  public @NonNull CharSequence getText(@NonNull Context context) {
    return context.getResources().getQuantityString(R.plurals.PendingGroupJoinRequestsReminder_d_pending_member_requests, count, count);
  }

  @Override
  public boolean isDismissable() {
    return true;
  }

  @Override
  public @NonNull Importance getImportance() {
    return Importance.NORMAL;
  }
}
