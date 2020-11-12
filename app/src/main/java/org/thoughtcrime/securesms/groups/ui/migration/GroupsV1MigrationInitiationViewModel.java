package org.thoughtcrime.securesms.groups.ui.migration;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.RecipientId;

class GroupsV1MigrationInitiationViewModel extends ViewModel {

  private final RecipientId                      groupRecipientId;
  private final MutableLiveData<MigrationState>  migrationState;
  private final GroupsV1MigrationRepository      repository;

  private GroupsV1MigrationInitiationViewModel(@NonNull RecipientId groupRecipientId) {
    this.groupRecipientId = groupRecipientId;
    this.migrationState   = new MutableLiveData<>();
    this.repository       = new GroupsV1MigrationRepository();

    repository.getMigrationState(groupRecipientId, migrationState::postValue);
  }

  @NonNull LiveData<MigrationState> getMigrationState() {
    return migrationState;
  }

  @NonNull LiveData<MigrationResult> onUpgradeClicked() {
    MutableLiveData <MigrationResult> migrationResult = new MutableLiveData<>();

    repository.upgradeGroup(groupRecipientId, migrationResult::postValue);

    return migrationResult;
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final RecipientId groupRecipientId;

    Factory(@NonNull RecipientId groupRecipientId) {
      this.groupRecipientId = groupRecipientId;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new GroupsV1MigrationInitiationViewModel(groupRecipientId));
    }
  }
}
