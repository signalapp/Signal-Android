/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.components.menu.SignalContextMenu;
import org.thoughtcrime.securesms.contacts.management.ContactsManagementRepository;
import org.thoughtcrime.securesms.contacts.management.ContactsManagementViewModel;
import org.thoughtcrime.securesms.contacts.paged.ChatType;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientRepository;
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity;
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Activity container for starting a new conversation.
 *
 * @author Moxie Marlinspike
 */
public class NewConversationActivity extends ContactSelectionActivity
    implements ContactSelectionListFragment.NewConversationCallback, ContactSelectionListFragment.OnItemLongClickListener, ContactSelectionListFragment.FindByCallback
{

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(NewConversationActivity.class);

  private ContactsManagementViewModel        viewModel;
  private ActivityResultLauncher<Intent>     contactLauncher;
  private ActivityResultLauncher<Intent>     createGroupLauncher;
  private ActivityResultLauncher<FindByMode> findByLauncher;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    super.onCreate(bundle, ready);
    assert getSupportActionBar() != null;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.NewConversationActivity__new_message);

    disposables.bindTo(this);

    ContactsManagementRepository        repository = new ContactsManagementRepository(this);
    ContactsManagementViewModel.Factory factory    = new ContactsManagementViewModel.Factory(repository);

    contactLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), activityResult -> {
      if (activityResult.getResultCode() != RESULT_CANCELED) {
        handleManualRefresh();
      }
    });

    findByLauncher = registerForActivityResult(new FindByActivity.Contract(), result -> {
      if (result != null) {
        launch(result);
      }
    });

    createGroupLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
      if (result.getResultCode() == RESULT_OK) {
        finish();
      }
    });

    viewModel = new ViewModelProvider(this, factory).get(ContactsManagementViewModel.class);
  }

  @Override
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, String number, @NonNull Optional<ChatType> chatType, @NonNull Consumer<Boolean> callback) {
    if (recipientId.isPresent()) {
      launch(Recipient.resolved(recipientId.get()));
    } else {
      Log.i(TAG, "[onContactSelected] Maybe creating a new recipient.");

      if (SignalStore.account().isRegistered()) {
        Log.i(TAG, "[onContactSelected] Doing contact refresh.");

        AlertDialog progress = SimpleProgressDialog.show(this);

        SimpleTask.run(getLifecycle(), () -> RecipientRepository.lookupNewE164(this, number), result -> {
          progress.dismiss();

          if (result instanceof RecipientRepository.LookupResult.Success) {
            Recipient resolved = Recipient.resolved(((RecipientRepository.LookupResult.Success) result).getRecipientId());
            if (resolved.isRegistered() && resolved.getHasServiceId()) {
              launch(resolved);
            }
          } else if (result instanceof RecipientRepository.LookupResult.NotFound || result instanceof RecipientRepository.LookupResult.InvalidEntry) {
            new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.NewConversationActivity__s_is_not_a_signal_user, number))
                .setPositiveButton(android.R.string.ok, null)
                .show();
          } else {
            new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.NetworkFailure__network_error_check_your_connection_and_try_again)
                .setPositiveButton(android.R.string.ok, null)
                .show();
          }
        });
      }
    }

    callback.accept(true);
  }

  @Override
  public void onSelectionChanged() {
  }

  private void launch(Recipient recipient) {
    launch(recipient.getId());
  }


  private void launch(RecipientId recipientId) {
    Disposable disposable = ConversationIntents.createBuilder(this, recipientId, -1L)
                                               .map(builder -> builder
                                                   .withDraftText(getIntent().getStringExtra(Intent.EXTRA_TEXT))
                                                   .withDataUri(getIntent().getData())
                                                   .withDataType(getIntent().getType())
                                                   .build())
                                               .subscribe(intent -> {
                                                 startActivity(intent);
                                                 finish();
                                               });

    disposables.add(disposable);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int itemId = item.getItemId();

    if (itemId == android.R.id.home) {
      super.onBackPressed();
      return true;
    } else if (itemId == R.id.menu_refresh) {
      handleManualRefresh();
      return true;
    } else if (itemId == R.id.menu_new_group) {
      handleCreateGroup();
      return true;
    } else if (itemId == R.id.menu_invite) {
      handleInvite();
      return true;
    } else {
      return false;
    }
  }

  private void handleManualRefresh() {
    contactsFragment.setRefreshing(true);
    onRefresh();
  }

  private void handleCreateGroup() {
    createGroupLauncher.launch(CreateGroupActivity.newIntent(this));
  }

  private void handleInvite() {
    startActivity(new Intent(this, InviteActivity.class));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.clear();
    getMenuInflater().inflate(R.menu.new_conversation_activity, menu);

    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public void onInvite() {
    handleInvite();
    finish();
  }

  @Override
  public void onNewGroup(boolean forceV1) {
    handleCreateGroup();
//    finish();
  }

  @Override
  public void onFindByUsername() {
    findByLauncher.launch(FindByMode.USERNAME);
  }

  @Override
  public void onFindByPhoneNumber() {
    findByLauncher.launch(FindByMode.PHONE_NUMBER);
  }

  @Override
  public boolean onLongClick(View anchorView, ContactSearchKey contactSearchKey, RecyclerView recyclerView) {
    RecipientId      recipientId = contactSearchKey.requireRecipientSearchKey().getRecipientId();
    List<ActionItem> actions     = generateContextualActionsForRecipient(recipientId);
    if (actions.isEmpty()) {
      return false;
    }

    new SignalContextMenu.Builder(anchorView, (ViewGroup) anchorView.getRootView())
        .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
        .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
        .offsetX((int) DimensionUnit.DP.toPixels(12))
        .offsetY((int) DimensionUnit.DP.toPixels(12))
        .onDismiss(() -> recyclerView.suppressLayout(false))
        .show(actions);

    recyclerView.suppressLayout(true);

    return true;
  }

  private @NonNull List<ActionItem> generateContextualActionsForRecipient(@NonNull RecipientId recipientId) {
    Recipient recipient = Recipient.resolved(recipientId);

    return Stream.of(
        createMessageActionItem(recipient),
        createAudioCallActionItem(recipient),
        createVideoCallActionItem(recipient),
        createRemoveActionItem(recipient),
        createBlockActionItem(recipient)
    ).filter(Objects::nonNull).collect(Collectors.toList());
  }

  private @NonNull ActionItem createMessageActionItem(@NonNull Recipient recipient) {
    return new ActionItem(
        R.drawable.ic_chat_message_24,
        getString(R.string.NewConversationActivity__message),
        R.color.signal_colorOnSurface,
        () -> {
          Disposable disposable = ConversationIntents.createBuilder(this, recipient.getId(), -1L)
                                                     .subscribe(builder -> startActivity(builder.build()));

          disposables.add(disposable);
        }
    );
  }

  private @Nullable ActionItem createAudioCallActionItem(@NonNull Recipient recipient) {
    if (recipient.isSelf() || recipient.isGroup()) {
      return null;
    }

    if (recipient.isRegistered()) {
      return new ActionItem(
          R.drawable.ic_phone_right_24,
          getString(R.string.NewConversationActivity__audio_call),
          R.color.signal_colorOnSurface,
          () -> CommunicationActions.startVoiceCall(this, recipient, () -> {
            YouAreAlreadyInACallSnackbar.show(findViewById(android.R.id.content));
          })
      );
    } else {
      return null;
    }
  }

  private @Nullable ActionItem createVideoCallActionItem(@NonNull Recipient recipient) {
    if (recipient.isSelf() || recipient.isMmsGroup() || !recipient.isRegistered()) {
      return null;
    }

    return new ActionItem(
        R.drawable.ic_video_call_24,
        getString(R.string.NewConversationActivity__video_call),
        R.color.signal_colorOnSurface,
        () -> CommunicationActions.startVideoCall(this, recipient, () -> {
          YouAreAlreadyInACallSnackbar.show(findViewById(android.R.id.content));
        })
    );
  }

  private @Nullable ActionItem createRemoveActionItem(@NonNull Recipient recipient) {
    if (recipient.isSelf() || recipient.isGroup()) {
      return null;
    }

    return new ActionItem(
        R.drawable.ic_minus_circle_20, // TODO [alex] -- correct asset
        getString(R.string.NewConversationActivity__remove),
        R.color.signal_colorOnSurface,
        () -> displayRemovalDialog(recipient)
    );
  }

  @SuppressWarnings("CodeBlock2Expr")
  private @Nullable ActionItem createBlockActionItem(@NonNull Recipient recipient) {
    if (recipient.isSelf()) {
      return null;
    }

    return new ActionItem(
        R.drawable.ic_block_tinted_24,
        getString(R.string.NewConversationActivity__block),
        R.color.signal_colorError,
        () -> BlockUnblockDialog.showBlockFor(this,
                                              this.getLifecycle(),
                                              recipient,
                                              () -> {
                                                disposables.add(viewModel.blockContact(recipient).subscribe(() -> {
                                                  displaySnackbar(R.string.NewConversationActivity__s_has_been_blocked, recipient.getDisplayName(this));
                                                  contactsFragment.reset();
                                                }));
                                              })
    );
  }

  private void displayIsInSystemContactsDialog(@NonNull Recipient recipient) {
    new MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.NewConversationActivity__unable_to_remove_s, recipient.getShortDisplayName(this)))
        .setMessage(R.string.NewConversationActivity__this_person_is_saved_to_your)
        .setPositiveButton(R.string.NewConversationActivity__view_contact,
                           (dialog, which) -> contactLauncher.launch(new Intent(Intent.ACTION_VIEW, recipient.getContactUri()))
        )
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void displayRemovalDialog(@NonNull Recipient recipient) {
    new MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.NewConversationActivity__remove_s, recipient.getShortDisplayName(this)))
        .setMessage(R.string.NewConversationActivity__you_wont_see_this_person)
        .setPositiveButton(R.string.NewConversationActivity__remove,
                           (dialog, which) -> {
                             disposables.add(viewModel.hideContact(recipient).subscribe(() -> {
                               onRefresh();
                               displaySnackbar(R.string.NewConversationActivity__s_has_been_removed, recipient.getDisplayName(this));
                             }));
                           }
        )
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void displaySnackbar(@StringRes int message, Object... formatArgs) {
    Snackbar.make(findViewById(android.R.id.content), getString(message, formatArgs), Snackbar.LENGTH_SHORT).show();
  }
}
