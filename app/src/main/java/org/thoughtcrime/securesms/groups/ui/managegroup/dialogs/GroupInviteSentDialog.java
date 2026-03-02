package org.thoughtcrime.securesms.groups.ui.managegroup.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GroupInviteSentDialog extends DialogFragment {
  private static final String FRAGMENT_TAG     = "GroupInviteSentDialog";
  public static final  String RESULT_DISMISSED = "GroupInviteSentDialog.result_dismissed";

  private static final String ARG_RECIPIENT_IDS = "recipient_ids";


  public static void show(@NonNull FragmentManager fragmentManager, @NonNull List<Recipient> recipients) {
    ArrayList<RecipientId> recipientIds = new ArrayList<>(recipients.size());
    for (Recipient recipient : recipients) {
      recipientIds.add(recipient.getId());
    }

    Bundle args = new Bundle();
    args.putParcelableArrayList(ARG_RECIPIENT_IDS, recipientIds);

    GroupInviteSentDialog fragment = new GroupInviteSentDialog();
    fragment.setArguments(args);
    fragment.show(fragmentManager, FRAGMENT_TAG);
  }

  @Override
  public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    List<RecipientId> recipientIds = Objects.requireNonNull(requireArguments().getParcelableArrayList(ARG_RECIPIENT_IDS));

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
        .setTitle(getResources().getQuantityString(R.plurals.GroupManagement_invitation_sent, recipientIds.size(), recipientIds.size()))
        .setPositiveButton(android.R.string.ok, null);

    if (recipientIds.size() == 1) {
      Recipient recipient = Recipient.live(recipientIds.get(0)).get();
      builder.setMessage(getString(R.string.GroupManagement_invite_single_user, recipient.getDisplayName(requireContext())));
    } else {
      builder.setMessage(R.string.GroupManagement_invite_multiple_users)
             .setView(R.layout.dialog_multiple_group_invites_sent);
    }

    return builder.create();
  }

  @Override
  public void onStart() {
    super.onStart();

    List<RecipientId> recipientIds = Objects.requireNonNull(requireArguments().getParcelableArrayList(ARG_RECIPIENT_IDS));

    if (recipientIds.size() > 1) {
      GroupMemberListView invitees = requireDialog().findViewById(R.id.list_invitees);
      invitees.initializeAdapter(this);

      List<GroupMemberEntry.PendingMember> pendingMembers = new ArrayList<>(recipientIds.size());
      for (RecipientId id : recipientIds) {
        pendingMembers.add(new GroupMemberEntry.PendingMember(Recipient.live(id).get()));
      }

      invitees.setMembers(pendingMembers);
    }
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    getParentFragmentManager().setFragmentResult(RESULT_DISMISSED, new Bundle());
  }
}
