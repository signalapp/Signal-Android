package org.thoughtcrime.securesms;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;

public final class GroupMembersDialog {

  private final FragmentActivity fragmentActivity;
  private final Recipient        groupRecipient;

  public GroupMembersDialog(@NonNull FragmentActivity activity,
                            @NonNull Recipient groupRecipient)
  {
    this.fragmentActivity = activity;
    this.groupRecipient   = groupRecipient;
  }

  public void display() {
      AlertDialog dialog = new AlertDialog.Builder(fragmentActivity)
                                          .setTitle(R.string.ConversationActivity_group_members)
                                          .setIconAttribute(R.attr.group_members_dialog_icon)
                                          .setCancelable(true)
                                          .setView(R.layout.dialog_group_members)
                                          .setPositiveButton(android.R.string.ok, null)
                                          .show();

      GroupMemberListView memberListView = dialog.findViewById(R.id.list_members);

      LiveGroup liveGroup = new LiveGroup(groupRecipient.requireGroupId());

      //noinspection ConstantConditions
      liveGroup.getFullMembers().observe(fragmentActivity, memberListView::setMembers);

      dialog.setOnDismissListener(d -> liveGroup.removeObservers(fragmentActivity));

      memberListView.setRecipientClickListener(recipient -> {
        dialog.dismiss();
        contactClick(recipient);
      });
  }

  private void contactClick(@NonNull Recipient recipient) {
    RecipientBottomSheetDialogFragment.create(recipient.getId(), groupRecipient.requireGroupId())
                                      .show(fragmentActivity.getSupportFragmentManager(), "BOTTOM");
  }
}
