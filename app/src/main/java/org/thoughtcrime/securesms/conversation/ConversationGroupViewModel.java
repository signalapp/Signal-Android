package org.thoughtcrime.securesms.conversation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.io.IOException;

final class ConversationGroupViewModel extends ViewModel {

  private final MutableLiveData<Recipient>          liveRecipient;
  private final LiveData<GroupActiveState>          groupActiveState;
  private final LiveData<GroupDatabase.MemberLevel> selfMembershipLevel;
  private final LiveData<Integer>                   actionableRequestingMembers;

  private ConversationGroupViewModel() {
    this.liveRecipient = new MutableLiveData<>();

    LiveData<GroupRecord> groupRecord = LiveDataUtil.mapAsync(liveRecipient, ConversationGroupViewModel::getGroupRecordForRecipient);

    this.groupActiveState            = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToGroupActiveState));
    this.selfMembershipLevel         = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToSelfMembershipLevel));
    this.actionableRequestingMembers = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToActionableRequestingMemberCount));
  }

  void onRecipientChange(Recipient recipient) {
    liveRecipient.setValue(recipient);
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
        FeatureFlags.groupsV2manageGroupLinks() &&
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
