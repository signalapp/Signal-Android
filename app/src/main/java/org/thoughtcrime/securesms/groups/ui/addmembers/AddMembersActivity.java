package org.thoughtcrime.securesms.groups.ui.addmembers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PushContactSelectionActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class AddMembersActivity extends PushContactSelectionActivity {

  public static final String GROUP_ID           = "group_id";
  public static final String ANNOUNCEMENT_GROUP = "announcement_group";

  private View                done;
  private AddMembersViewModel viewModel;

  public static @NonNull Intent createIntent(@NonNull Context context,
                                             @NonNull GroupId groupId,
                                             int displayModeFlags,
                                             int selectionWarning,
                                             int selectionLimit,
                                             boolean isAnnouncementGroup,
                                             @NonNull List<RecipientId> membersWithoutSelf)  {
    Intent intent = new Intent(context, AddMembersActivity.class);

    intent.putExtra(GROUP_ID, groupId.toString());
    intent.putExtra(ANNOUNCEMENT_GROUP, isAnnouncementGroup);
    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayModeFlags);
    intent.putExtra(ContactSelectionListFragment.SELECTION_LIMITS, new SelectionLimits(selectionWarning, selectionLimit));
    intent.putParcelableArrayListExtra(ContactSelectionListFragment.CURRENT_SELECTION, new ArrayList<>(membersWithoutSelf));
    intent.putExtra(ContactSelectionListFragment.RV_PADDING_BOTTOM, (int) DimensionUnit.DP.toPixels(64f));
    intent.putExtra(ContactSelectionListFragment.RV_CLIP, false);

    return intent;
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    getIntent().putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.add_members_activity);
    super.onCreate(icicle, ready);

    AddMembersViewModel.Factory factory = new AddMembersViewModel.Factory(getGroupId());

    done      = findViewById(R.id.done);
    viewModel = new ViewModelProvider(this, factory).get(AddMembersViewModel.class);

    done.setOnClickListener(v ->
      viewModel.getDialogStateForSelectedContacts(contactsFragment.getSelectedContacts(), this::displayAlertMessage)
    );

    disableDone();
  }

  @Override
  protected void initializeToolbar() {
    getToolbar().setNavigationIcon(R.drawable.ic_arrow_left_24);
    getToolbar().setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }

  @Override
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, String number, @NonNull Consumer<Boolean> callback) {
    if (getGroupId().isV1() && recipientId.isPresent() && !Recipient.resolved(recipientId.get()).hasE164()) {
      Toast.makeText(this, R.string.AddMembersActivity__this_person_cant_be_added_to_legacy_groups, Toast.LENGTH_SHORT).show();
      callback.accept(false);
      return;
    }

    if (contactsFragment.hasQueryFilter()) {
      getContactFilterView().clear();
    }

    enableDone();

    callback.accept(true);
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getContactFilterView().clear();
    }

    if (contactsFragment.getSelectedContactsCount() < 1) {
      disableDone();
    }
  }

  @Override
  public void onSelectionChanged() {
    int selectedContactsCount = contactsFragment.getTotalMemberCount() + 1;
    if (selectedContactsCount == 0) {
      getToolbar().setTitle(getString(R.string.AddMembersActivity__add_members));
    } else {
      getToolbar().setTitle(getResources().getQuantityString(R.plurals.CreateGroupActivity__d_members, selectedContactsCount, selectedContactsCount));
    }
  }

  private void enableDone() {
    done.setEnabled(true);
    done.animate().alpha(1f);
  }

  private void disableDone() {
    done.setEnabled(false);
    done.animate().alpha(0.5f);
  }

  private GroupId getGroupId() {
    return GroupId.parseOrThrow(getIntent().getStringExtra(GROUP_ID));
  }

  private boolean isAnnouncementGroup() {
    return getIntent().getBooleanExtra(ANNOUNCEMENT_GROUP, false);
  }

  private void displayAlertMessage(@NonNull AddMembersViewModel.AddMemberDialogMessageState state) {
    Recipient recipient = Util.firstNonNull(state.getRecipient(), Recipient.UNKNOWN);

    String message = getResources().getQuantityString(R.plurals.AddMembersActivity__add_d_members_to_s, state.getSelectionCount(),
                                                      recipient.getDisplayName(this), state.getGroupTitle(), state.getSelectionCount());

    new MaterialAlertDialogBuilder(this)
                   .setMessage(message)
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                   .setPositiveButton(R.string.AddMembersActivity__add, (dialog, which) -> {
                     dialog.dismiss();
                     onFinishedSelection();
                   })
                   .setCancelable(true)
                   .show();
  }
}
