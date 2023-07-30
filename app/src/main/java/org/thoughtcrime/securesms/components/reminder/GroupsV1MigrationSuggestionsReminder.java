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
  private final int suggestionsSize;

  public GroupsV1MigrationSuggestionsReminder(@NonNull List<RecipientId> suggestions) {
    this.suggestionsSize = suggestions.size();

    addAction(new AddMembersAction(suggestionsSize));
    addAction(new Action(R.string.GroupsV1MigrationSuggestionsReminder_no_thanks, R.id.reminder_action_gv1_suggestion_no_thanks));
  }

  @Override
  public @NonNull CharSequence getText(@NonNull Context context) {
    return context.getResources().getQuantityString(R.plurals.GroupsV1MigrationSuggestionsReminder_members_couldnt_be_added_to_the_new_group, suggestionsSize, suggestionsSize);
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  private static class AddMembersAction extends Action {
    private final int suggestionsSize;

    public AddMembersAction(int suggestionsSize) {
      super(NO_RESOURCE, R.id.reminder_action_gv1_suggestion_add_members);
      this.suggestionsSize = suggestionsSize;
    }

    @Override
    public CharSequence getTitle(@NonNull Context context) {
      return context.getResources().getQuantityString(R.plurals.GroupsV1MigrationSuggestionsReminder_add_members, suggestionsSize);
    }
  }
}
