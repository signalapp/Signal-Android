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
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;

public class ManageGroupViewModel extends ViewModel {

  private static final int MAX_COLLAPSED_MEMBERS = 5;

  private final Context                                     context;
  private final ManageGroupRepository                       manageGroupRepository;
  private final LiveData<String>                            title;
  private final LiveData<Boolean>                           isAdmin;
  private final LiveData<Boolean>                           canEditGroupAttributes;
  private final LiveData<Boolean>                           canAddMembers;
  private final LiveData<List<GroupMemberEntry.FullMember>> members;
  private final LiveData<Integer>                           pendingMemberCount;
  private final LiveData<String>                            disappearingMessageTimer;
  private final LiveData<String>                            memberCountSummary;
  private final LiveData<String>                            fullMemberCountSummary;
  private final LiveData<GroupAccessControl>                editMembershipRights;
  private final LiveData<GroupAccessControl>                editGroupAttributesRights;
  private final LiveData<Recipient>                         groupRecipient;
  private final MutableLiveData<GroupViewState>             groupViewState            = new MutableLiveData<>(null);
  private final LiveData<MuteState>                         muteState;
  private final LiveData<Boolean>                           hasCustomNotifications;
  private final LiveData<Boolean>                           canCollapseMemberList;
  private final DefaultValueLiveData<CollapseState>         memberListCollapseState   = new DefaultValueLiveData<>(CollapseState.COLLAPSED);

  private ManageGroupViewModel(@NonNull Context context, @NonNull ManageGroupRepository manageGroupRepository) {
    this.context               = context;
    this.manageGroupRepository = manageGroupRepository;

    manageGroupRepository.getGroupState(this::groupStateLoaded);

    LiveGroup liveGroup = new LiveGroup(manageGroupRepository.getGroupId());

    this.title                     = liveGroup.getTitle();
    this.isAdmin                   = liveGroup.isSelfAdmin();
    this.canCollapseMemberList     = LiveDataUtil.combineLatest(memberListCollapseState,
                                                                Transformations.map(liveGroup.getFullMembers(), m -> m.size() > MAX_COLLAPSED_MEMBERS),
                                                                (state, hasEnoughMembers) -> state != CollapseState.OPEN && hasEnoughMembers);
    this.members                   = LiveDataUtil.combineLatest(liveGroup.getFullMembers(),
                                                                memberListCollapseState,
                                                                this::filterMemberList);
    this.pendingMemberCount        = liveGroup.getPendingMemberCount();
    this.memberCountSummary        = liveGroup.getMembershipCountDescription(context.getResources());
    this.fullMemberCountSummary    = liveGroup.getFullMembershipCountDescription(context.getResources());
    this.editMembershipRights      = liveGroup.getMembershipAdditionAccessControl();
    this.editGroupAttributesRights = liveGroup.getAttributesAccessControl();
    this.disappearingMessageTimer  = Transformations.map(liveGroup.getExpireMessages(), expiration -> ExpirationUtil.getExpirationDisplayValue(context, expiration));
    this.canEditGroupAttributes    = liveGroup.selfCanEditGroupAttributes();
    this.canAddMembers             = liveGroup.selfCanAddMembers();
    this.groupRecipient            = liveGroup.getGroupRecipient();
    this.muteState                 = Transformations.map(this.groupRecipient,
                                                         recipient -> new MuteState(recipient.getMuteUntil(), recipient.isMuted()));
    this.hasCustomNotifications    = Transformations.map(this.groupRecipient,
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

  LiveData<String> getFullMemberCountSummary() {
    return fullMemberCountSummary;
  }

  public LiveData<Recipient> getGroupRecipient() {
    return groupRecipient;
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

  LiveData<Boolean> getCanAddMembers() {
    return canAddMembers;
  }

  LiveData<String> getDisappearingMessageTimer() {
    return disappearingMessageTimer;
  }

  LiveData<Boolean> hasCustomNotifications() {
    return hasCustomNotifications;
  }

  public LiveData<Boolean> getCanCollapseMemberList() {
    return canCollapseMemberList;
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

  void onAddMembers(List<RecipientId> selected) {
    manageGroupRepository.addMembers(selected, this::showErrorToast);
  }

  void setMuteUntil(long muteUntil) {
    manageGroupRepository.setMuteUntil(muteUntil);
  }

  void clearMuteUntil() {
    manageGroupRepository.setMuteUntil(0);
  }

  void revealCollapsedMembers() {
    memberListCollapseState.setValue(CollapseState.OPEN);
  }

  private @NonNull List<GroupMemberEntry.FullMember> filterMemberList(@NonNull List<GroupMemberEntry.FullMember> members,
                                                                      @NonNull CollapseState collapseState)
  {
    if (collapseState == CollapseState.COLLAPSED && members.size() > MAX_COLLAPSED_MEMBERS) {
      return members.subList(0, MAX_COLLAPSED_MEMBERS);
    } else {
      return members;
    }
  }

  @WorkerThread
  private void showErrorToast(@NonNull GroupChangeFailureReason e) {
    Util.runOnMain(() -> Toast.makeText(context, GroupErrors.getUserDisplayMessage(e), Toast.LENGTH_LONG).show());
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

  private enum CollapseState {
    OPEN,
    COLLAPSED
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
