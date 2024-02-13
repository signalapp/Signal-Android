package org.thoughtcrime.securesms;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;

import java.util.List;

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
    AlertDialog dialog = new MaterialAlertDialogBuilder(fragmentActivity)
                                        .setTitle(R.string.ConversationActivity_group_members)
                                        .setIcon(R.drawable.ic_group_24)
                                        .setCancelable(true)
                                        .setView(R.layout.dialog_group_members)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();

    GroupMemberListView memberListView = dialog.findViewById(R.id.list_members);
    memberListView.initializeAdapter(fragmentActivity);

    LiveGroup                                   liveGroup   = new LiveGroup(groupRecipient.requireGroupId());
    LiveData<List<GroupMemberEntry.FullMember>> fullMembers = liveGroup.getFullMembers();

    //noinspection ConstantConditions
    fullMembers.observe(fragmentActivity, memberListView::setMembers);

    dialog.setOnDismissListener(d -> fullMembers.removeObservers(fragmentActivity));

    memberListView.setRecipientClickListener(recipient -> {
      dialog.dismiss();
      contactClick(recipient);
    });
  }

  private void contactClick(@NonNull Recipient recipient) {
    RecipientBottomSheetDialogFragment.show(fragmentActivity.getSupportFragmentManager(), recipient.getId(), groupRecipient.requireGroupId());
  }
}
