package org.thoughtcrime.securesms.conversation;

import android.app.Application;
import android.content.Context;

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
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewRecipient;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ConversationGroupViewModel extends ViewModel {

  private static final long GV1_MIGRATION_REMINDER_INTERVAL = TimeUnit.DAYS.toMillis(1);

  private final MutableLiveData<Recipient>          liveRecipient;
  private final LiveData<GroupActiveState>          groupActiveState;
  private final LiveData<GroupDatabase.MemberLevel> selfMembershipLevel;
  private final LiveData<Integer>                   actionableRequestingMembers;
  private final LiveData<ReviewState>               reviewState;
  private final LiveData<List<RecipientId>>         gv1MigrationSuggestions;
  private final LiveData<Boolean>                   gv1MigrationReminder;

  private boolean firstTimeInviteFriendsTriggered;

  private ConversationGroupViewModel() {
    this.liveRecipient = new MutableLiveData<>();

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
    this.gv1MigrationReminder        = Transformations.distinctUntilChanged(LiveDataUtil.mapAsync(groupRecord, ConversationGroupViewModel::mapToGroupV1MigrationReminder));
    this.reviewState                 = LiveDataUtil.combineLatest(groupRecord,
                                                                  duplicates,
                                                                  (record, dups) -> dups.isEmpty()
                                                                                    ? ReviewState.EMPTY
                                                                                    : new ReviewState(record.getId().requireV2(), dups.get(0), dups.size()));

  }

  void onRecipientChange(Recipient recipient) {
    liveRecipient.setValue(recipient);
  }

  void onSuggestedMembersBannerDismissed(@NonNull GroupId groupId, @NonNull List<RecipientId> suggestions) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (groupId.isV2()) {
        DatabaseFactory.getGroupDatabase(ApplicationDependencies.getApplication()).removeUnmigratedV1Members(groupId.requireV2(), suggestions);
        liveRecipient.postValue(liveRecipient.getValue());
      }
    });
  }

  void onMigrationInitiationReminderBannerDismissed(@NonNull RecipientId recipientId) {
    SignalExecutors.BOUNDED.execute(() -> {
      DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication()).markGroupsV1MigrationReminderSeen(recipientId, System.currentTimeMillis());
      liveRecipient.postValue(liveRecipient.getValue());
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

  LiveData<GroupDatabase.MemberLevel> getSelfMemberLevel() {
    return selfMembershipLevel;
  }

  public LiveData<ReviewState> getReviewState() {
    return reviewState;
  }

  @NonNull LiveData<List<RecipientId>> getGroupV1MigrationSuggestions() {
    return gv1MigrationSuggestions;
  }

  @NonNull LiveData<Boolean> getShowGroupsV1MigrationBanner() {
    return gv1MigrationReminder;
  }

  private static @Nullable GroupRecord getGroupRecordForRecipient(@Nullable Recipient recipient) {
    if (recipient != null && recipient.isGroup()) {
      Application context         = ApplicationDependencies.getApplication();
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
      return groupDatabase.getGroup(recipient.getId()).orNull();
    } else {
      return null;
    }
  }

  private static int mapToActionableRequestingMemberCount(@Nullable GroupRecord record) {
    if (record != null                          &&
        record.isV2Group()                      &&
        record.memberLevel(Recipient.self()) == GroupDatabase.MemberLevel.ADMINISTRATOR)
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

  private static GroupDatabase.MemberLevel mapToSelfMembershipLevel(@Nullable GroupRecord record) {
    if (record == null) {
      return null;
    }
    return record.memberLevel(Recipient.self());
  }

  @WorkerThread
  private static List<RecipientId> mapToGroupV1MigrationSuggestions(@Nullable GroupRecord record) {
    if (record == null) {
      return Collections.emptyList();
    }

    if (!record.isV2Group()) {
      return Collections.emptyList();
    }

    if (!record.isActive() || record.isPendingMember(Recipient.self())) {
      return Collections.emptyList();
    }

    return Stream.of(record.getUnmigratedV1Members())
                 .filterNot(m -> record.getMembers().contains(m))
                 .map(Recipient::resolved)
                 .filter(GroupsV1MigrationUtil::isAutoMigratable)
                 .map(Recipient::getId)
                 .toList();
  }

  @WorkerThread
  private static boolean mapToGroupV1MigrationReminder(@Nullable GroupRecord record) {
    if (record == null                          ||
        !record.isV1Group()                     ||
        !record.isActive()                      ||
        !FeatureFlags.groupsV1ManualMigration() ||
        FeatureFlags.groupsV1ForcedMigration()  ||
        !Recipient.resolved(record.getRecipientId()).isProfileSharing())
    {
      return false;
    }

    boolean canAutoMigrate = Stream.of(Recipient.resolvedList(record.getMembers()))
                                   .allMatch(GroupsV1MigrationUtil::isAutoMigratable);

    if (canAutoMigrate) {
      return false;
    }

    Context context          = ApplicationDependencies.getApplication();
    long    lastReminderTime = DatabaseFactory.getRecipientDatabase(context).getGroupsV1MigrationReminderLastSeen(record.getRecipientId());

    return System.currentTimeMillis() - lastReminderTime > GV1_MIGRATION_REMINDER_INTERVAL;
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

    SimpleTask.run(() -> DatabaseFactory.getGroupDatabase(ApplicationDependencies.getApplication())
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

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationGroupViewModel());
    }
  }
}
