package org.thoughtcrime.securesms.recipients.ui.notifications;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

public final class CustomNotificationsViewModel extends ViewModel {

  private final LiveData<Boolean>                        hasCustomNotifications;
  private final LiveData<RecipientDatabase.VibrateState> isVibrateEnabled;
  private final LiveData<Uri>                            notificationSound;
  private final CustomNotificationsRepository            repository;
  private final MutableLiveData<Boolean>                 isInitialLoadComplete = new MutableLiveData<>();
  private final LiveData<Boolean>                        showCallingOptions;
  private final LiveData<Uri>                            ringtone;
  private final LiveData<RecipientDatabase.VibrateState> isCallingVibrateEnabled;

  private CustomNotificationsViewModel(@NonNull RecipientId recipientId, @NonNull CustomNotificationsRepository repository) {
    LiveData<Recipient> recipient = Recipient.live(recipientId).getLiveData();

    this.repository              = repository;
    this.hasCustomNotifications  = Transformations.map(recipient, r -> r.getNotificationChannel() != null || !NotificationChannels.supported());
    this.isVibrateEnabled        = Transformations.map(recipient, Recipient::getMessageVibrate);
    this.notificationSound       = Transformations.map(recipient, Recipient::getMessageRingtone);
    this.showCallingOptions      = Transformations.map(recipient, r -> !r.isGroup() && r.isRegistered());
    this.ringtone                = Transformations.map(recipient, Recipient::getCallRingtone);
    this.isCallingVibrateEnabled = Transformations.map(recipient, Recipient::getCallVibrate);

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

  public void setCallSound(@Nullable Uri sound) {
    repository.setCallSound(sound);
  }

  public LiveData<Boolean> getShowCallingOptions() {
    return showCallingOptions;
  }

  public LiveData<Uri> getRingtone() {
    return ringtone;
  }

  public LiveData<RecipientDatabase.VibrateState> getCallingVibrateState() {
    return isCallingVibrateEnabled;
  }

  public void setCallingVibrate(@NonNull RecipientDatabase.VibrateState vibrateState) {
    repository.setCallingVibrate(vibrateState);
  }

  public static final class Factory implements ViewModelProvider.Factory {

    private final RecipientId                   recipientId;
    private final CustomNotificationsRepository repository;

    public Factory(@NonNull RecipientId recipientId, @NonNull CustomNotificationsRepository repository) {
      this.recipientId = recipientId;
      this.repository  = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new CustomNotificationsViewModel(recipientId, repository));
    }
  }
}
