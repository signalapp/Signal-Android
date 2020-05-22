package org.thoughtcrime.securesms.groups.ui.notifications;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.recipients.Recipient;

public final class CustomNotificationsViewModel extends ViewModel {

  private final LiveGroup                                liveGroup;
  private final LiveData<Boolean>                        hasCustomNotifications;
  private final LiveData<RecipientDatabase.VibrateState> isVibrateEnabled;
  private final LiveData<Uri>                            notificationSound;
  private final CustomNotificationsRepository            repository;
  private final MutableLiveData<Boolean>                 isInitialLoadComplete = new MutableLiveData<>();

  private CustomNotificationsViewModel(@NonNull GroupId groupId, @NonNull CustomNotificationsRepository repository) {
    this.liveGroup              = new LiveGroup(groupId);
    this.repository             = repository;
    this.hasCustomNotifications = Transformations.map(liveGroup.getGroupRecipient(), recipient -> recipient.getNotificationChannel() != null);
    this.isVibrateEnabled       = Transformations.map(liveGroup.getGroupRecipient(), Recipient::getMessageVibrate);
    this.notificationSound      = Transformations.map(liveGroup.getGroupRecipient(), Recipient::getMessageRingtone);

    repository.onLoad(() -> isInitialLoadComplete.postValue(true));
  }

  public LiveData<Boolean> isInitialLoadComplete() {
    return isInitialLoadComplete;
  }

  public LiveData<Boolean> hasCustomNotifications() {
    return hasCustomNotifications;
  }

  public LiveData<RecipientDatabase.VibrateState> getVibrateState() {
    return isVibrateEnabled;
  }

  public LiveData<Uri> getNotificationSound() {
    return notificationSound;
  }

  public void setHasCustomNotifications(boolean hasCustomNotifications) {
    repository.setHasCustomNotifications(hasCustomNotifications);
  }

  public void setMessageVibrate(@NonNull RecipientDatabase.VibrateState vibrateState) {
    repository.setMessageVibrate(vibrateState);
  }

  public void setMessageSound(@Nullable Uri sound) {
    repository.setMessageSound(sound);
  }

  public static final class Factory implements ViewModelProvider.Factory {

    private final GroupId                       groupId;
    private final CustomNotificationsRepository repository;

    public Factory(@NonNull GroupId groupId, @NonNull CustomNotificationsRepository repository) {
      this.groupId    = groupId;
      this.repository = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new CustomNotificationsViewModel(groupId, repository));
    }
  }
}
