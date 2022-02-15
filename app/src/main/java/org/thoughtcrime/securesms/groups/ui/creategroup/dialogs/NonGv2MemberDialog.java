package org.thoughtcrime.securesms.groups.ui.creategroup.dialogs;

import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.ArrayList;
import java.util.List;

public final class NonGv2MemberDialog {

  private NonGv2MemberDialog() {
  }

  public static @Nullable Dialog showNonGv2Members(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner, @NonNull List<Recipient> recipients) {
    int size = recipients.size();
    if (size == 0) {
      return null;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                                 // TODO: GV2 Need a URL for learn more
                                                 //  .setNegativeButton(R.string.NonGv2MemberDialog_learn_more, (dialog, which) -> {
                                                 //  })
                                                 .setPositiveButton(android.R.string.ok, null);
    if (size == 1) {
      builder.setMessage(context.getString(R.string.NonGv2MemberDialog_single_users_are_non_gv2_capable_forced_migration, recipients.get(0).getDisplayName(context)));
    } else {
      builder.setMessage(context.getResources().getQuantityString(R.plurals.NonGv2MemberDialog_d_users_are_non_gv2_capable_forced_migration, size, size))
             .setView(R.layout.dialog_multiple_members_non_gv2_capable);
    }

    Dialog dialog = builder.show();
    if (size > 1) {
      GroupMemberListView nonGv2CapableMembers = dialog.findViewById(R.id.list_non_gv2_members);

      nonGv2CapableMembers.initializeAdapter(lifecycleOwner);

      List<GroupMemberEntry.NewGroupCandidate> pendingMembers = new ArrayList<>(recipients.size());
      for (Recipient r : recipients) {
        pendingMembers.add(new GroupMemberEntry.NewGroupCandidate(r));
      }

      //noinspection ConstantConditions
      nonGv2CapableMembers.setMembers(pendingMembers);
    }

    return dialog;
  }
}
