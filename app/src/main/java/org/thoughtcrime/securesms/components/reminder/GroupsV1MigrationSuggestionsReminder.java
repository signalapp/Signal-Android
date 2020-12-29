package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;

/**
 * Shows a reminder to add anyone that might have been missed in GV1->GV2 migration.
 */
public class GroupsV1MigrationSuggestionsReminder extends Reminder {
  public GroupsV1MigrationSuggestionsReminder(@NonNull Context context, @NonNull List<RecipientId> suggestions) {
    super(null, context.getResources().getQuantityString(R.plurals.GroupsV1MigrationSuggestionsReminder_members_couldnt_be_added_to_the_new_group, suggestions.size(), suggestions.size()));
    addAction(new Action(context.getResources().getQuantityString(R.plurals.GroupsV1MigrationSuggestionsReminder_add_members, suggestions.size()), R.id.reminder_action_gv1_suggestion_add_members));
    addAction(new Action(context.getResources().getString(R.string.GroupsV1MigrationSuggestionsReminder_no_thanks), R.id.reminder_action_gv1_suggestion_no_thanks));
  }

  @Override
  public boolean isDismissable() {
    return false;
  }
}
