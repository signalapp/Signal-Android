package org.thoughtcrime.securesms.recipients.ui.bottomsheet;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.BlockUnblockDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.StoryViewState;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsActivity;
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.stories.StoryViewerArgs;
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity;

import java.util.Objects;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

final class RecipientDialogViewModel extends ViewModel {

  private final Context                         context;
  private final RecipientDialogRepository       recipientDialogRepository;
  private final LiveData<Recipient>             recipient;
  private final MutableLiveData<IdentityRecord> identity;
  private final LiveData<AdminActionStatus>     adminActionStatus;
  private final LiveData<Boolean>               canAddToAGroup;
  private final MutableLiveData<Boolean>        adminActionBusy;
  private final MutableLiveData<StoryViewState> storyViewState;
  private final CompositeDisposable             disposables;
  private final boolean                         isDeprecatedOrUnregistered;
  private RecipientDialogViewModel(@NonNull Context context,
                                   @NonNull RecipientDialogRepository recipientDialogRepository)
  {
    this.context                    = context;
    this.recipientDialogRepository  = recipientDialogRepository;
    this.identity                   = new MutableLiveData<>();
    this.adminActionBusy            = new MutableLiveData<>(false);
    this.storyViewState             = new MutableLiveData<>();
    this.disposables                = new CompositeDisposable();
    this.isDeprecatedOrUnregistered = SignalStore.misc().isClientDeprecated() || TextSecurePreferences.isUnauthorizedReceived(context);

    boolean recipientIsSelf = recipientDialogRepository.getRecipientId().equals(Recipient.self().getId());

    recipient = Recipient.live(recipientDialogRepository.getRecipientId()).getLiveData();

    if (recipientDialogRepository.getGroupId() != null && recipientDialogRepository.getGroupId().isV2() && !recipientIsSelf) {
      LiveGroup source = new LiveGroup(recipientDialogRepository.getGroupId());

      LiveData<Pair<Boolean, Boolean>> localStatus          = LiveDataUtil.combineLatest(source.isSelfAdmin(), Transformations.map(source.getGroupLink(), s -> s == null || s.isEnabled()), Pair::new);
      LiveData<GroupTable.MemberLevel> recipientMemberLevel = Transformations.switchMap(recipient, source::getMemberLevel);

      adminActionStatus = LiveDataUtil.combineLatest(localStatus, recipientMemberLevel, (statuses, memberLevel) -> {
        boolean localAdmin     = statuses.first();
        boolean isLinkActive   = statuses.second();
        boolean inGroup        = memberLevel.isInGroup();
        boolean recipientAdmin = memberLevel == GroupTable.MemberLevel.ADMINISTRATOR;

        return new AdminActionStatus(inGroup && localAdmin,
                                     inGroup && localAdmin && !recipientAdmin,
                                     inGroup && localAdmin && recipientAdmin,
                                     isLinkActive);
      });
    } else {
      adminActionStatus = new MutableLiveData<>(new AdminActionStatus(false, false, false, false));
    }

    boolean isSelf = recipientDialogRepository.getRecipientId().equals(Recipient.self().getId());
    if (!isSelf) {
      recipientDialogRepository.getIdentity(identity::postValue);
    }

    MutableLiveData<Integer> localGroupCount = new MutableLiveData<>(0);

    canAddToAGroup = LiveDataUtil.combineLatest(recipient, localGroupCount,
                                                (r, count) -> count > 0 && r.isRegistered() && !r.isGroup() && !r.isSelf() && !r.isBlocked());

    recipientDialogRepository.getActiveGroupCount(localGroupCount::postValue);

    Disposable storyViewStateDisposable = StoryViewState.getForRecipientId(recipientDialogRepository.getRecipientId())
                                                        .subscribe(storyViewState::postValue);

    disposables.add(storyViewStateDisposable);
  }

  @Override protected void onCleared() {
    super.onCleared();
    disposables.clear();
  }

  boolean isDeprecatedOrUnregistered() {
    return isDeprecatedOrUnregistered;
  }

  LiveData<StoryViewState> getStoryViewState() {
    return storyViewState;
  }

  LiveData<Recipient> getRecipient() {
    return recipient;
  }

  public LiveData<Boolean> getCanAddToAGroup() {
    return canAddToAGroup;
  }

  LiveData<AdminActionStatus> getAdminActionStatus() {
    return adminActionStatus;
  }

  LiveData<IdentityRecord> getIdentity() {
    return identity;
  }

  LiveData<Boolean> getAdminActionBusy() {
    return adminActionBusy;
  }

  void onNoteToSelfClicked(@NonNull Activity activity) {
    if (storyViewState.getValue() == null || storyViewState.getValue() == StoryViewState.NONE) {
      onMessageClicked(activity);
    } else {
      activity.startActivity(StoryViewerActivity.createIntent(
          activity,
          new StoryViewerArgs.Builder(recipientDialogRepository.getRecipientId(), recipient.getValue().getShouldHideStory())
                             .isFromQuote(true)
                             .build()));
    }
  }

  void onMessageClicked(@NonNull Activity activity) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startConversation(activity, recipient, null));
  }

  void onSecureCallClicked(@NonNull FragmentActivity activity, @NonNull CommunicationActions.OnUserAlreadyInAnotherCall onUserAlreadyInAnotherCall) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startVoiceCall(activity, recipient, onUserAlreadyInAnotherCall));
  }

  void onInsecureCallClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startInsecureCall(activity, recipient));
  }

  void onSecureVideoCallClicked(@NonNull FragmentActivity activity, @NonNull CommunicationActions.OnUserAlreadyInAnotherCall onUserAlreadyInAnotherCall) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startVideoCall(activity, recipient, onUserAlreadyInAnotherCall));
  }

  void onBlockClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> BlockUnblockDialog.showBlockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.blockNonGroup(context, recipient)));
  }

  void onUnblockClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> BlockUnblockDialog.showUnblockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.unblock(recipient)));
  }

  void onViewSafetyNumberClicked(@NonNull Activity activity, @NonNull IdentityRecord identityRecord) {
    VerifyIdentityActivity.startOrShowExchangeMessagesDialog(activity, identityRecord);
  }

  void onAvatarClicked(@NonNull Activity activity) {
    if (storyViewState.getValue() == null || storyViewState.getValue() == StoryViewState.NONE) {
      activity.startActivity(ConversationSettingsActivity.forRecipient(activity, recipientDialogRepository.getRecipientId()));
    } else {
      activity.startActivity(StoryViewerActivity.createIntent(
          activity,
          new StoryViewerArgs.Builder(recipientDialogRepository.getRecipientId(), recipient.getValue().getShouldHideStory())
                             .isFromQuote(true)
                             .build()));
    }
  }

  void onMakeGroupAdminClicked(@NonNull Activity activity) {
    new MaterialAlertDialogBuilder(activity)
                   .setMessage(context.getString(R.string.RecipientBottomSheet_s_will_be_able_to_edit_group, Objects.requireNonNull(recipient.getValue()).getDisplayName(context)))
                   .setPositiveButton(R.string.RecipientBottomSheet_make_admin,
                                      (dialog, which) -> {
                                        adminActionBusy.setValue(true);
                                        recipientDialogRepository.setMemberAdmin(true, result -> {
                                          adminActionBusy.setValue(false);
                                          if (!result) {
                                            Toast.makeText(activity, R.string.ManageGroupActivity_failed_to_update_the_group, Toast.LENGTH_SHORT).show();
                                          }
                                        },
                                        this::showErrorToast);
                                      })
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                   .show();
  }

  void onRemoveGroupAdminClicked(@NonNull Activity activity) {
    new MaterialAlertDialogBuilder(activity)
                   .setMessage(context.getString(R.string.RecipientBottomSheet_remove_s_as_group_admin, Objects.requireNonNull(recipient.getValue()).getDisplayName(context)))
                   .setPositiveButton(R.string.RecipientBottomSheet_remove_as_admin,
                                      (dialog, which) -> {
                                        adminActionBusy.setValue(true);
                                        recipientDialogRepository.setMemberAdmin(false, result -> {
                                          adminActionBusy.setValue(false);
                                          if (!result) {
                                            Toast.makeText(activity, R.string.ManageGroupActivity_failed_to_update_the_group, Toast.LENGTH_SHORT).show();
                                          }
                                        },
                                        this::showErrorToast);
                                      })
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                   .show();
  }

  void onRemoveFromGroupClicked(@NonNull Activity activity, boolean isLinkActive, @NonNull Runnable onSuccess) {
    new MaterialAlertDialogBuilder(activity)
                   .setMessage(context.getString(isLinkActive ? R.string.RecipientBottomSheet_remove_s_from_the_group_they_will_not_be_able_to_rejoin
                                                              : R.string.RecipientBottomSheet_remove_s_from_the_group,
                                                 Objects.requireNonNull(recipient.getValue()).getDisplayName(context)))
                   .setPositiveButton(R.string.RecipientBottomSheet_remove,
                                      (dialog, which) -> {
                                        adminActionBusy.setValue(true);
                                        recipientDialogRepository.removeMember(result -> {
                                          adminActionBusy.setValue(false);
                                          if (result) {
                                            onSuccess.run();
                                          }
                                        },
                                        this::showErrorToast);
                                      })
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                   .show();
  }

  void refreshRecipient() {
    recipientDialogRepository.refreshRecipient();
  }

  void onAddToGroupButton(@NonNull Activity activity) {
    recipientDialogRepository.getGroupMembership(existingGroups -> activity.startActivity(AddToGroupsActivity.newIntent(activity, recipientDialogRepository.getRecipientId(), existingGroups)));
  }

  @WorkerThread
  private void showErrorToast(@NonNull GroupChangeFailureReason e) {
    ThreadUtil.runOnMain(() -> Toast.makeText(context, GroupErrors.getUserDisplayMessage(e), Toast.LENGTH_LONG).show());
  }

  public void onTapToViewAvatar(@NonNull Recipient recipient) {
    SignalExecutors.BOUNDED.execute(() -> SignalDatabase.recipients().manuallyUpdateShowAvatar(recipient.getId(), true));
    if (recipient.isPushV2Group()) {
      AvatarGroupsV2DownloadJob.enqueueUnblurredAvatar(recipient.requireGroupId().requireV2());
    } else {
      RetrieveProfileAvatarJob.enqueueUnblurredAvatar(recipient);
    }
  }

  public void onResetBlurAvatar(@NonNull Recipient recipient) {
    SignalExecutors.BOUNDED.execute(() -> SignalDatabase.recipients().manuallyUpdateShowAvatar(recipient.getId(), false));
  }

  public void refreshGroupId(@Nullable GroupId groupId) {
    if (groupId != null) {
      SignalExecutors.BOUNDED.execute(() -> {
        RecipientId groupRecipientId = SignalDatabase.groups().getGroup(groupId).get().getRecipientId();
        Recipient.live(groupRecipientId).refresh();
      });
    }
  }

  static class AdminActionStatus {
    private final boolean canRemove;
    private final boolean canMakeAdmin;
    private final boolean canMakeNonAdmin;
    private final boolean isLinkActive;

    AdminActionStatus(boolean canRemove, boolean canMakeAdmin, boolean canMakeNonAdmin, boolean isLinkActive) {
      this.canRemove       = canRemove;
      this.canMakeAdmin    = canMakeAdmin;
      this.canMakeNonAdmin = canMakeNonAdmin;
      this.isLinkActive    = isLinkActive;
    }

    boolean isCanRemove() {
      return canRemove;
    }

    boolean isCanMakeAdmin() {
      return canMakeAdmin;
    }

    boolean isCanMakeNonAdmin() {
      return canMakeNonAdmin;
    }

    boolean isLinkActive() {
      return isLinkActive;
    }
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final Context     context;
    private final RecipientId recipientId;
    private final GroupId     groupId;

    Factory(@NonNull Context context, @NonNull RecipientId recipientId, @Nullable GroupId groupId) {
      this.context     = context;
      this.recipientId = recipientId;
      this.groupId     = groupId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new RecipientDialogViewModel(context, new RecipientDialogRepository(context, recipientId, groupId));
    }
  }
}
