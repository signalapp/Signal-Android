package org.thoughtcrime.securesms.groups.ui.migration;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

class GroupsV1MigrationInfoViewModel extends ViewModel {

  private final MutableLiveData<List<Recipient>> pendingMembers;
  private final MutableLiveData<List<Recipient>> droppedMembers;

  private GroupsV1MigrationInfoViewModel(@NonNull GroupMigrationMembershipChange membershipChange) {
    this.pendingMembers = new MutableLiveData<>();
    this.droppedMembers = new MutableLiveData<>();

    SignalExecutors.BOUNDED.execute(() -> {
      this.pendingMembers.postValue(Recipient.resolvedList(membershipChange.getPending()));
    });

    SignalExecutors.BOUNDED.execute(() -> {
      this.droppedMembers.postValue(Recipient.resolvedList(membershipChange.getDropped()));
    });
  }

  @NonNull LiveData<List<Recipient>> getPendingMembers() {
    return pendingMembers;
  }

  @NonNull LiveData<List<Recipient>> getDroppedMembers() {
    return droppedMembers;
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final GroupMigrationMembershipChange membershipChange;

    Factory(@NonNull GroupMigrationMembershipChange membershipChange) {
      this.membershipChange = membershipChange;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new GroupsV1MigrationInfoViewModel(membershipChange));
    }
  }
}
