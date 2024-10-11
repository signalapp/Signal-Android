package org.thoughtcrime.securesms.groups.ui.addmembers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PushContactSelectionActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.paged.ChatType;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientRepository;
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity;
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class AddMembersActivity extends PushContactSelectionActivity implements ContactSelectionListFragment.FindByCallback {

  public static final String GROUP_ID           = "group_id";
  public static final String ANNOUNCEMENT_GROUP = "announcement_group";

  private View                               done;
  private AddMembersViewModel                viewModel;
  private ActivityResultLauncher<FindByMode> findByActivityLauncher;

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

    findByActivityLauncher = registerForActivityResult(new FindByActivity.Contract(), result -> {
      if (result != null) {
        contactsFragment.addRecipientToSelectionIfAble(result);
      }
    });
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
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, String number, @NonNull Optional<ChatType> chatType, @NonNull Consumer<Boolean> callback) {
    if (getGroupId().isV1() && recipientId.isPresent() && !Recipient.resolved(recipientId.get()).getHasE164()) {
      Toast.makeText(this, R.string.AddMembersActivity__this_person_cant_be_added_to_legacy_groups, Toast.LENGTH_SHORT).show();
      callback.accept(false);
      return;
    }

    if (contactsFragment.hasQueryFilter()) {
      getContactFilterView().clear();
    }

    if (recipientId.isPresent()) {
      callback.accept(true);
      enableDone();
      return;
    }

    AlertDialog progress = SimpleProgressDialog.show(this);

    SimpleTask.run(getLifecycle(), () -> RecipientRepository.lookupNewE164(this, number), result -> {
      progress.dismiss();

      if (result instanceof RecipientRepository.LookupResult.Success) {
        enableDone();
        callback.accept(true);
      } else if (result instanceof RecipientRepository.LookupResult.NotFound || result instanceof RecipientRepository.LookupResult.InvalidEntry) {
        new MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.NewConversationActivity__s_is_not_a_signal_user, number))
            .setPositiveButton(android.R.string.ok, null)
            .show();
        callback.accept(false);
      } else {
        new MaterialAlertDialogBuilder(this)
            .setMessage(R.string.NetworkFailure__network_error_check_your_connection_and_try_again)
            .setPositiveButton(android.R.string.ok, null)
            .show();
        callback.accept(false);
      }
    });
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, String number, @NonNull Optional<ChatType> chatType) {
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

  @Override
  public void onFindByPhoneNumber() {
    findByActivityLauncher.launch(FindByMode.PHONE_NUMBER);
  }

  @Override
  public void onFindByUsername() {
    findByActivityLauncher.launch(FindByMode.USERNAME);
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
