package org.thoughtcrime.securesms.conversation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.GroupTable.GroupRecord;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.v2.GroupBlockJoinRequestResult;
import org.thoughtcrime.securesms.groups.v2.GroupManagementRepository;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewRecipient;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.signal.core.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;

final class ConversationGroupViewModel extends ViewModel {

  private final MutableLiveData<Recipient>          liveRecipient;
  private final LiveData<GroupActiveState>          groupActiveState;
  private final LiveData<ConversationMemberLevel>   selfMembershipLevel;
  private final LiveData<Integer>                   actionableRequestingMembers;
  private final LiveData<ReviewState>               reviewState;
  private final LiveData<List<RecipientId>>         gv1MigrationSuggestions;
  private final GroupManagementRepository           groupManagementRepository;

  private boolean firstTimeInviteFriendsTriggered;

  private ConversationGroupViewModel() {
    this.liveRecipient             = new MutableLiveData<>();
    this.groupManagementRepository = new GroupManagementRepository();

    LiveData<GroupRecord>     groupRecord = LiveDataUtil.mapAsync(liveRecipient, ConversationGroupViewModel::getGroupRecordForRecipient);
    LiveData<List<Recipient>> duplicates  = LiveDataUtil.mapAsync(groupRecord, record -> {
      if (record != null && record.isV2Group()) {
        return Stream.of(ReviewUtil.getDuplicatedRecipients(record.getId().requireV2()))
                                   .map(ReviewRecipient::getRecipient)
                                   .toList();
      } else {
        return Collections.emptyList();
      }
    });

    this.groupActiveState            = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToGroupActiveState));
    this.selfMembershipLevel         = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToSelfMembershipLevel));
    this.actionableRequestingMembers = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToActionableRequestingMemberCount));
    this.gv1MigrationSuggestions     = Transformations.distinctUntilChanged(LiveDataUtil.mapAsync(groupRecord, ConversationGroupViewModel::mapToGroupV1MigrationSuggestions));
    this.reviewState                 = LiveDataUtil.combineLatest(groupRecord,
                                                                  duplicates,
                                                                  (record, dups) -> dups.isEmpty()
                                                                                    ? ReviewState.EMPTY
                                                                                    : new ReviewState(record.getId().requireV2(), dups.get(0), dups.size()));
  }

  void onRecipientChange(Recipient recipient) {
    liveRecipient.setValue(recipient);
  }

  void onSuggestedMembersBannerDismissed(@NonNull GroupId groupId) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (groupId.isV2()) {
        SignalDatabase.groups().removeUnmigratedV1Members(groupId.requireV2());
        liveRecipient.postValue(liveRecipient.getValue());
      }
    });
  }

  /**
   * The number of pending group join requests that can be actioned by this client.
   */
  LiveData<Integer> getActionableRequestingMembers() {
    return actionableRequestingMembers;
  }

  LiveData<GroupActiveState> getGroupActiveState() {
    return groupActiveState;
  }

  LiveData<ConversationMemberLevel> getSelfMemberLevel() {
    return selfMembershipLevel;
  }

  public LiveData<ReviewState> getReviewState() {
    return reviewState;
  }

  @NonNull LiveData<List<RecipientId>> getGroupV1MigrationSuggestions() {
    return gv1MigrationSuggestions;
  }

  boolean isNonAdminInAnnouncementGroup() {
    ConversationMemberLevel level = selfMembershipLevel.getValue();
    return level != null && level.getMemberLevel() != GroupTable.MemberLevel.ADMINISTRATOR && level.isAnnouncementGroup();
  }

  private static @Nullable GroupRecord getGroupRecordForRecipient(@Nullable Recipient recipient) {
    if (recipient != null && recipient.isGroup()) {
      Application context       = ApplicationDependencies.getApplication();
      GroupTable  groupDatabase = SignalDatabase.groups();
      return groupDatabase.getGroup(recipient.getId()).orElse(null);
    } else {
      return null;
    }
  }

  private static int mapToActionableRequestingMemberCount(@Nullable GroupRecord record) {
    if (record != null                          &&
        record.isV2Group()                      &&
        record.memberLevel(Recipient.self()) == GroupTable.MemberLevel.ADMINISTRATOR)
    {
      return record.requireV2GroupProperties()
                   .getDecryptedGroup()
                   .getRequestingMembersCount();
    } else {
      return 0;
    }
  }

  private static GroupActiveState mapToGroupActiveState(@Nullable GroupRecord record) {
    if (record == null) {
      return null;
    }
    return new GroupActiveState(record.isActive(), record.isV2Group());
  }

  private static ConversationMemberLevel mapToSelfMembershipLevel(@Nullable GroupRecord record) {
    if (record == null) {
      return null;
    }
    return new ConversationMemberLevel(record.memberLevel(Recipient.self()), record.isAnnouncementGroup());
  }

  @WorkerThread
  private static List<RecipientId> mapToGroupV1MigrationSuggestions(@Nullable GroupRecord record) {
    if (record == null ||
        !record.isV2Group() ||
        !record.isActive() ||
        record.isPendingMember(Recipient.self())) {
      return Collections.emptyList();
    }

    return Stream.of(record.getUnmigratedV1Members())
                 .filterNot(m -> record.getMembers().contains(m))
                 .map(Recipient::resolved)
                 .filter(GroupsV1MigrationUtil::isAutoMigratable)
                 .map(Recipient::getId)
                 .toList();
  }

  public static void onCancelJoinRequest(@NonNull Recipient recipient,
                                         @NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      if (!recipient.isPushV2Group()) {
        throw new AssertionError();
      }

      try {
        GroupManager.cancelJoinRequest(ApplicationDependencies.getApplication(), recipient.getGroupId().get().requireV2());
        callback.onComplete(null);
      } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
        callback.onError(GroupChangeFailureReason.fromException(e));
      }
    });
  }

  void inviteFriendsOneTimeIfJustSelfInGroup(@NonNull FragmentManager supportFragmentManager, @NonNull GroupId.V2 groupId) {
    if (firstTimeInviteFriendsTriggered) {
      return;
    }

    firstTimeInviteFriendsTriggered = true;

    SimpleTask.run(() -> SignalDatabase.groups()
                                       .requireGroup(groupId)
                                       .getMembers().equals(Collections.singletonList(Recipient.self().getId())),
                   justSelf -> {
                     if (justSelf) {
                       inviteFriends(supportFragmentManager, groupId);
                     }
                   }
    );
  }

  void inviteFriends(@NonNull FragmentManager supportFragmentManager, @NonNull GroupId.V2 groupId) {
    GroupLinkInviteFriendsBottomSheetDialogFragment.show(supportFragmentManager, groupId);
  }

  public Single<GroupBlockJoinRequestResult> blockJoinRequests(@NonNull Recipient groupRecipient, @NonNull Recipient recipient) {
    return groupManagementRepository.blockJoinRequests(groupRecipient.requireGroupId().requireV2(), recipient)
        .observeOn(AndroidSchedulers.mainThread());
  }

  static final class ReviewState {

    private static final ReviewState EMPTY = new ReviewState(null, Recipient.UNKNOWN, 0);

    private final GroupId.V2 groupId;
    private final Recipient  recipient;
    private final int        count;

    ReviewState(@Nullable GroupId.V2 groupId, @NonNull Recipient recipient, int count) {
      this.groupId   = groupId;
      this.recipient = recipient;
      this.count     = count;
    }

    public @Nullable GroupId.V2 getGroupId() {
      return groupId;
    }

    public @NonNull Recipient getRecipient() {
      return recipient;
    }

    public int getCount() {
      return count;
    }
  }

  static final class GroupActiveState {
    private final boolean isActive;
    private final boolean isActiveV2;

    public GroupActiveState(boolean isActive, boolean isV2) {
      this.isActive   = isActive;
      this.isActiveV2 = isActive && isV2;
    }

    public boolean isActiveGroup() {
      return isActive;
    }

    public boolean isActiveV2Group() {
      return isActiveV2;
    }
  }

  static final class ConversationMemberLevel {
    private final GroupTable.MemberLevel memberLevel;
    private final boolean                isAnnouncementGroup;

    private ConversationMemberLevel(GroupTable.MemberLevel memberLevel, boolean isAnnouncementGroup) {
      this.memberLevel         = memberLevel;
      this.isAnnouncementGroup = isAnnouncementGroup;
    }

    public @NonNull GroupTable.MemberLevel getMemberLevel() {
      return memberLevel;
    }

    public boolean isAnnouncementGroup() {
      return isAnnouncementGroup;
    }
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationGroupViewModel());
    }
  }
}
