package org.thoughtcrime.securesms.groups.ui.migration;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.MembershipNotSuitableForV2Exception;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shows a list of members that got lost when migrating from a V1->V2 group, giving you the chance
 * to add them back.
 */
public final class GroupsV1MigrationSuggestionsDialog extends DialogFragment {

  private static final String TAG          = Log.tag(GroupsV1MigrationSuggestionsDialog.class);
  private static final String FRAGMENT_TAG = "GroupsV1MigrationSuggestionsDialog";

  private static final String ARG_GROUP_ID    = "group_id";
  private static final String ARG_SUGGESTIONS = "suggestions";

  private GroupId.V2        groupId;
  private List<RecipientId> suggestions;

  public static void show(@NonNull FragmentManager fragmentManager,
                          @NonNull GroupId.V2 groupId,
                          @NonNull List<RecipientId> suggestions)
  {
    Bundle args = new Bundle();
    args.putParcelable(ARG_GROUP_ID, groupId);
    args.putParcelableArrayList(ARG_SUGGESTIONS, new ArrayList<>(suggestions));

    GroupsV1MigrationSuggestionsDialog fragment = new GroupsV1MigrationSuggestionsDialog();
    fragment.setArguments(args);
    fragment.show(fragmentManager, FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    groupId     = ((GroupId) Objects.requireNonNull(requireArguments().getParcelable(ARG_GROUP_ID))).requireV2();
    suggestions = Objects.requireNonNull(requireArguments().getParcelableArrayList(ARG_SUGGESTIONS));
  }

  @Override
  public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle(requireContext().getResources().getQuantityString(R.plurals.GroupsV1MigrationSuggestionsDialog_add_members_question, suggestions.size()))
        .setMessage(requireContext().getResources().getQuantityString(R.plurals.GroupsV1MigrationSuggestionsDialog_these_members_couldnt_be_automatically_added, suggestions.size()))
        .setView(R.layout.dialog_group_members)
        .setPositiveButton(requireContext().getResources().getQuantityString(R.plurals.GroupsV1MigrationSuggestionsDialog_add_members, suggestions.size()), (d, i) -> onAddClicked(d))
        .setNegativeButton(android.R.string.cancel, (d, i) -> d.dismiss())
        .create();
  }

  @Override
  public void onStart() {
    super.onStart();

    GroupMemberListView memberListView = requireDialog().findViewById(R.id.list_members);

    memberListView.initializeAdapter(this);

    SimpleTask.run(() -> Recipient.resolvedList(suggestions),
                   memberListView::setDisplayOnlyMembers);
  }

  private void onAddClicked(@NonNull DialogInterface rootDialog) {
    FragmentActivity activity = requireActivity();

    SimpleProgressDialog.DismissibleDialog progressDialog = SimpleProgressDialog.showDelayed(activity, 300, 0);
    SimpleTask.run(SignalExecutors.UNBOUNDED, () -> {
      try {
        GroupManager.addMembers(activity, groupId.requirePush(), suggestions);
        Log.i(TAG, "Successfully added members! Removing these dropped members from the list.");
        SignalDatabase.groups().removeUnmigratedV1Members(groupId);
        return Result.SUCCESS;
      } catch (IOException | GroupChangeBusyException e) {
        Log.w(TAG, "Temporary failure.", e);
        return Result.NETWORK_ERROR;
      } catch (GroupNotAMemberException | GroupInsufficientRightsException | MembershipNotSuitableForV2Exception | GroupChangeFailedException e) {
        Log.w(TAG, "Permanent failure! Removing these dropped members from the list.", e);
        SignalDatabase.groups().removeUnmigratedV1Members(groupId);
        return Result.IMPOSSIBLE;
      }
    }, result -> {
      progressDialog.dismiss();
      rootDialog.dismiss();

      switch (result) {
        case NETWORK_ERROR:
          Toast.makeText(activity, activity.getResources().getQuantityText(R.plurals.GroupsV1MigrationSuggestionsDialog_failed_to_add_members_try_again_later, suggestions.size()), Toast.LENGTH_SHORT).show();
          break;
        case IMPOSSIBLE:
          Toast.makeText(activity, activity.getResources().getQuantityText(R.plurals.GroupsV1MigrationSuggestionsDialog_cannot_add_members, suggestions.size()), Toast.LENGTH_SHORT).show();
          break;
      }
    });
  }

  private enum Result {
    SUCCESS, NETWORK_ERROR, IMPOSSIBLE
  }
}
