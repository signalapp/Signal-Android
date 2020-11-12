package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;

/**
 * Shows a reminder to upgrade a group to GV2.
 */
public class GroupsV1MigrationInitiationReminder extends Reminder {

  public GroupsV1MigrationInitiationReminder(@NonNull Context context) {
    super(null, context.getString(R.string.GroupsV1MigrationInitiationReminder_to_access_new_features_like_mentions));
    addAction(new Action(context.getString(R.string.GroupsV1MigrationInitiationReminder_update_group), R.id.reminder_action_gv1_initiation_update_group));
    addAction(new Action(context.getResources().getString(R.string.GroupsV1MigrationInitiationReminder_not_now), R.id.reminder_action_gv1_initiation_not_now));
  }

  @Override
  public boolean isDismissable() {
    return false;
  }
}
