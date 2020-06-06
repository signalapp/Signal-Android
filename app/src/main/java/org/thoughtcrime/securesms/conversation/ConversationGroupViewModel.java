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
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

class ConversationGroupViewModel extends ViewModel {

  private final MutableLiveData<Recipient> liveRecipient;
  private final LiveData<GroupActiveState> groupActiveState;

  private ConversationGroupViewModel() {
    liveRecipient                     = new MutableLiveData<>();
    LiveData<GroupRecord> groupRecord = LiveDataUtil.mapAsync(liveRecipient, this::getGroupRecordForRecipient);
    groupActiveState                  = Transformations.distinctUntilChanged(Transformations.map(groupRecord, this::mapToGroupActiveState));
  }

  void onRecipientChange(Recipient recipient) {
    liveRecipient.setValue(recipient);
  }

  LiveData<GroupActiveState> getGroupActiveState() {
    return groupActiveState;
  }

  private GroupRecord getGroupRecordForRecipient(Recipient recipient) {
    if (recipient != null && recipient.isGroup()) {
      Application context         = ApplicationDependencies.getApplication();
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
      return groupDatabase.getGroup(recipient.getId()).orNull();
    } else {
      return null;
    }
  }

  private GroupActiveState mapToGroupActiveState(@Nullable GroupRecord record) {
    if (record == null) {
      return null;
    }
    return new GroupActiveState(record.isActive(), record.isV2Group());
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
