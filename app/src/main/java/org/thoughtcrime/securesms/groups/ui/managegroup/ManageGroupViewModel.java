package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.content.Context;
import android.database.Cursor;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.BlockUnblockDialog;
import org.thoughtcrime.securesms.ExpirationDialog;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.database.loaders.MediaLoader;
import org.thoughtcrime.securesms.database.loaders.ThreadMediaLoader;
import org.thoughtcrime.securesms.groups.GroupAccessControl;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

public class ManageGroupViewModel extends ViewModel {

  private final Context                                     context;
  private final ManageGroupRepository                       manageGroupRepository;
  private final LiveData<String>                            title;
  private final LiveData<Boolean>                           isAdmin;
  private final LiveData<Boolean>                           canEditGroupAttributes;
  private final LiveData<List<GroupMemberEntry.FullMember>> members;
  private final LiveData<Integer>                           pendingMemberCount;
  private final LiveData<String>                            disappearingMessageTimer;
  private final LiveData<String>                            memberCountSummary;
  private final LiveData<GroupAccessControl>                editMembershipRights;
  private final LiveData<GroupAccessControl>                editGroupAttributesRights;
  private final MutableLiveData<GroupViewState>             groupViewState            = new MutableLiveData<>(null);
  private final LiveData<MuteState>                         muteState;
  private final LiveData<Boolean>                           hasCustomNotifications;

  private ManageGroupViewModel(@NonNull Context context, @NonNull ManageGroupRepository manageGroupRepository) {
    this.context               = context;
    this.manageGroupRepository = manageGroupRepository;

    manageGroupRepository.getGroupState(this::groupStateLoaded);

    LiveGroup liveGroup = new LiveGroup(manageGroupRepository.getGroupId());

    this.title                     = liveGroup.getTitle();
    this.isAdmin                   = liveGroup.isSelfAdmin();
    this.members                   = liveGroup.getFullMembers();
    this.pendingMemberCount        = liveGroup.getPendingMemberCount();
    this.memberCountSummary        = liveGroup.getMembershipCountDescription(context.getResources());
    this.editMembershipRights      = liveGroup.getMembershipAdditionAccessControl();
    this.editGroupAttributesRights = liveGroup.getAttributesAccessControl();
    this.disappearingMessageTimer  = Transformations.map(liveGroup.getExpireMessages(), expiration -> ExpirationUtil.getExpirationDisplayValue(context, expiration));
    this.canEditGroupAttributes    = liveGroup.selfCanEditGroupAttributes();
    this.muteState                 = Transformations.map(liveGroup.getGroupRecipient(),
                                                         recipient -> new MuteState(recipient.getMuteUntil(), recipient.isMuted()));
    this.hasCustomNotifications    = Transformations.map(liveGroup.getGroupRecipient(),
                                                         recipient -> recipient.getNotificationChannel() != null);
  }

  @WorkerThread
  private void groupStateLoaded(@NonNull ManageGroupRepository.GroupStateResult groupStateResult) {
    groupViewState.postValue(new GroupViewState(groupStateResult.getThreadId(),
                                                groupStateResult.getRecipient(),
                                                () -> new ThreadMediaLoader(context, groupStateResult.getThreadId(), MediaLoader.MediaType.GALLERY, MediaDatabase.Sorting.Newest).getCursor()));
  }

  LiveData<List<GroupMemberEntry.FullMember>> getMembers() {
    return members;
  }

  LiveData<Integer> getPendingMemberCount() {
    return pendingMemberCount;
  }

  LiveData<String> getMemberCountSummary() {
    return memberCountSummary;
  }

  LiveData<GroupViewState> getGroupViewState() {
    return groupViewState;
  }

  LiveData<String> getTitle() {
    return title;
  }

  LiveData<MuteState> getMuteState() {
    return muteState;
  }

  LiveData<GroupAccessControl> getMembershipRights() {
    return editMembershipRights;
  }

  LiveData<GroupAccessControl> getEditGroupAttributesRights() {
    return editGroupAttributesRights;
  }

  LiveData<Boolean> getIsAdmin() {
    return isAdmin;
  }

  LiveData<Boolean> getCanEditGroupAttributes() {
    return canEditGroupAttributes;
  }

  LiveData<String> getDisappearingMessageTimer() {
    return disappearingMessageTimer;
  }

  LiveData<Boolean> hasCustomNotifications() {
    return hasCustomNotifications;
  }

  void handleExpirationSelection() {
    manageGroupRepository.getRecipient(groupRecipient ->
                                         ExpirationDialog.show(context,
                                                               groupRecipient.getExpireMessages(),
                                                               expirationTime -> manageGroupRepository.setExpiration(expirationTime, this::showErrorToast)));
  }

  void applyMembershipRightsChange(@NonNull GroupAccessControl newRights) {
    manageGroupRepository.applyMembershipRightsChange(newRights, this::showErrorToast);
  }

  void applyAttributesRightsChange(@NonNull GroupAccessControl newRights) {
    manageGroupRepository.applyAttributesRightsChange(newRights, this::showErrorToast);
  }

  void blockAndLeave(@NonNull FragmentActivity activity) {
    manageGroupRepository.getRecipient(recipient -> BlockUnblockDialog.showBlockFor(activity, activity.getLifecycle(), recipient,
                                       () -> RecipientUtil.block(context, recipient)));
  }

  void setMuteUntil(long muteUntil) {
    manageGroupRepository.setMuteUntil(muteUntil);
  }

  void clearMuteUntil() {
    manageGroupRepository.setMuteUntil(0);
  }

  @WorkerThread
  private void showErrorToast(@NonNull ManageGroupRepository.FailureReason e) {
    Util.runOnMain(() -> Toast.makeText(context, e.getToastMessage(), Toast.LENGTH_SHORT).show());
  }

  static final class GroupViewState {
             private final long          threadId;
    @NonNull private final Recipient     groupRecipient;
    @NonNull private final CursorFactory mediaCursorFactory;

    private GroupViewState(long threadId,
                           @NonNull Recipient groupRecipient,
                           @NonNull CursorFactory mediaCursorFactory)
    {
      this.threadId           = threadId;
      this.groupRecipient     = groupRecipient;
      this.mediaCursorFactory = mediaCursorFactory;
    }

    long getThreadId() {
      return threadId;
    }

    @NonNull Recipient getGroupRecipient() {
      return groupRecipient;
    }

    @NonNull CursorFactory getMediaCursorFactory() {
      return mediaCursorFactory;
    }
  }

  static final class MuteState {
    private final long    mutedUntil;
    private final boolean isMuted;

    MuteState(long mutedUntil, boolean isMuted) {
      this.mutedUntil = mutedUntil;
      this.isMuted    = isMuted;
    }

    public long getMutedUntil() {
      return mutedUntil;
    }

    public boolean isMuted() {
      return isMuted;
    }
  }

  interface CursorFactory {
    Cursor create();
  }

  public static class Factory implements ViewModelProvider.Factory {
    private final Context      context;
    private final GroupId.Push groupId;

    public Factory(@NonNull Context context, @NonNull GroupId.Push groupId) {
      this.context = context;
      this.groupId = groupId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new ManageGroupViewModel(context, new ManageGroupRepository(context.getApplicationContext(), groupId));
    }
  }
}
