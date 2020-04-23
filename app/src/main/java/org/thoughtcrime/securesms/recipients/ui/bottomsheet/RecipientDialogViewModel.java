package org.thoughtcrime.securesms.recipients.ui.bottomsheet;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.BlockUnblockDialog;
import org.thoughtcrime.securesms.RecipientPreferenceActivity;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.livedata.LiveDataPair;

final class RecipientDialogViewModel extends ViewModel {

  private final Context                                          context;
  private final RecipientDialogRepository                        recipientDialogRepository;
  private final LiveData<Recipient>                              recipient;
  private final MutableLiveData<IdentityDatabase.IdentityRecord> identity;
  private final LiveData<AdminActionStatus>                      adminActionStatus;

  private RecipientDialogViewModel(@NonNull Context context,
                                   @NonNull RecipientDialogRepository recipientDialogRepository)
  {
    this.context                   = context;
    this.recipientDialogRepository = recipientDialogRepository;
    this.identity                  = new MutableLiveData<>();

    MutableLiveData<Boolean> localIsAdmin     = new DefaultValueLiveData<>(false);
    MutableLiveData<Boolean> recipientIsAdmin = new DefaultValueLiveData<>(false);

    if (recipientDialogRepository.getGroupId() != null && recipientDialogRepository.getGroupId().isV2()) {
      recipientDialogRepository.isAdminOfGroup(Recipient.self().getId(), localIsAdmin::setValue);
      recipientDialogRepository.isAdminOfGroup(recipientDialogRepository.getRecipientId(), recipientIsAdmin::setValue);
    }

    adminActionStatus = Transformations.map(new LiveDataPair<>(localIsAdmin, recipientIsAdmin, false, false),
      pair -> {
        boolean localAdmin     = pair.first();
        boolean recipientAdmin = pair.second();

        return new AdminActionStatus(localAdmin,
                                     localAdmin && !recipientAdmin,
                                     localAdmin && recipientAdmin);
      });

    recipient = Recipient.live(recipientDialogRepository.getRecipientId()).getLiveData();

    recipientDialogRepository.getIdentity(identity::setValue);
  }

  LiveData<Recipient> getRecipient() {
    return recipient;
  }

  LiveData<AdminActionStatus> getAdminActionStatus() {
    return adminActionStatus;
  }

  LiveData<IdentityDatabase.IdentityRecord> getIdentity() {
    return identity;
  }

  void onMessageClicked(@NonNull Activity activity) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startConversation(activity, recipient, null));
  }

  void onSecureCallClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startVoiceCall(activity, recipient));
  }

  void onBlockClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> BlockUnblockDialog.showBlockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.block(context, recipient)));
  }

  void onUnblockClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> BlockUnblockDialog.showUnblockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.unblock(context, recipient)));
  }

  void onViewSafetyNumberClicked(@NonNull Activity activity, @NonNull IdentityDatabase.IdentityRecord identityRecord) {
    activity.startActivity(VerifyIdentityActivity.newIntent(activity, identityRecord));
  }

  void onAvatarClicked(@NonNull Activity activity) {
    activity.startActivity(RecipientPreferenceActivity.getLaunchIntent(activity, recipientDialogRepository.getRecipientId()));
  }

  void onMakeGroupAdminClicked() {
    // TODO GV2
    throw new AssertionError("NYI");
  }

  void onRemoveGroupAdminClicked() {
    // TODO GV2
    throw new AssertionError("NYI");
  }

  void onRemoveFromGroupClicked() {
    // TODO GV2
    throw new AssertionError("NYI");
  }

  static class AdminActionStatus {
    private final boolean canRemove;
    private final boolean canMakeAdmin;
    private final boolean canMakeNonAdmin;

    AdminActionStatus(boolean canRemove, boolean canMakeAdmin, boolean canMakeNonAdmin) {
      this.canRemove       = canRemove;
      this.canMakeAdmin    = canMakeAdmin;
      this.canMakeNonAdmin = canMakeNonAdmin;
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
