package org.thoughtcrime.securesms;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.util.ArrayList;

public final class GroupMembersDialog {

  private final FragmentActivity fragmentActivity;
  private final Recipient        groupRecipient;
  private final Lifecycle        lifecycle;

  public GroupMembersDialog(@NonNull FragmentActivity activity,
                            @NonNull Recipient groupRecipient,
                            @NonNull Lifecycle lifecycle)
  {
    this.fragmentActivity = activity;
    this.groupRecipient   = groupRecipient;
    this.lifecycle        = lifecycle;
  }

  public void display() {
    SimpleTask.run(
      lifecycle,
      () -> DatabaseFactory.getGroupDatabase(fragmentActivity).getGroupMembers(groupRecipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_INCLUDING_SELF),
      members -> {
        AlertDialog dialog = new AlertDialog.Builder(fragmentActivity)
                                            .setTitle(R.string.ConversationActivity_group_members)
                                            .setIconAttribute(R.attr.group_members_dialog_icon)
                                            .setCancelable(true)
                                            .setView(R.layout.dialog_group_members)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();

        GroupMemberListView memberListView = dialog.findViewById(R.id.list_members);

        ArrayList<GroupMemberEntry.FullMember> pendingMembers = new ArrayList<>(members.size());
        for (Recipient member : members) {
          GroupMemberEntry.FullMember entry = new GroupMemberEntry.FullMember(member);

          entry.setOnClick(() -> {
            dialog.dismiss();
            contactClick(member);
          });

          if (member.isLocalNumber()) {
            pendingMembers.add(0, entry);
          } else {
            pendingMembers.add(entry);
          }
        }

        //noinspection ConstantConditions
        memberListView.setMembers(pendingMembers);
      }
    );
  }

  private void contactClick(@NonNull Recipient recipient) {
    RecipientBottomSheetDialogFragment.create(recipient.getId(), groupRecipient.requireGroupId())
                                      .show(fragmentActivity.getSupportFragmentManager(), "BOTTOM");
  }
}
