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
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public final class CustomNotificationsViewModel extends ViewModel {

  private final LiveData<Boolean>                        hasCustomNotifications;
  private final LiveData<RecipientDatabase.VibrateState> messageVibrateState;
  private final LiveData<Uri>                            notificationSound;
  private final CustomNotificationsRepository            repository;
  private final MutableLiveData<Boolean>                 isInitialLoadComplete  = new MutableLiveData<>();
  private final LiveData<Boolean>                        showCallingOptions;
  private final LiveData<Uri>                            ringtone;
  private final LiveData<RecipientDatabase.VibrateState> callingVibrateState;
  private final LiveData<Boolean>                        messageVibrateToggle;

  private CustomNotificationsViewModel(@NonNull RecipientId recipientId, @NonNull CustomNotificationsRepository repository) {
    LiveData<Recipient> recipient = Recipient.live(recipientId).getLiveData();

    this.repository             = repository;
    this.hasCustomNotifications = Transformations.map(recipient, r -> r.getNotificationChannel() != null || !NotificationChannels.supported());
    this.callingVibrateState    = Transformations.map(recipient, Recipient::getCallVibrate);
    this.messageVibrateState    = Transformations.map(recipient, Recipient::getMessageVibrate);
    this.notificationSound      = Transformations.map(recipient, Recipient::getMessageRingtone);
    this.showCallingOptions     = Transformations.map(recipient, r -> !r.isGroup() && r.isRegistered());
    this.ringtone               = Transformations.map(recipient, Recipient::getCallRingtone);
    this.messageVibrateToggle   = Transformations.map(messageVibrateState, vibrateState -> {
                                                                             switch (vibrateState) {
                                                                               case DISABLED: return false;
                                                                               case ENABLED : return true;
                                                                               case DEFAULT : return TextSecurePreferences.isNotificationVibrateEnabled(ApplicationDependencies.getApplication());
                                                                               default      : throw new AssertionError();
                                                                             }
                                                                           });

    repository.onLoad(() -> isInitialLoadComplete.postValue(true));
  }

  LiveData<Boolean> isInitialLoadComplete() {
    return isInitialLoadComplete;
  }

  LiveData<Boolean> hasCustomNotifications() {
    return hasCustomNotifications;
  }

  LiveData<Uri> getNotificationSound() {
    return notificationSound;
  }

  LiveData<RecipientDatabase.VibrateState> getMessageVibrateState() {
    return messageVibrateState;
  }
  
  LiveData<Boolean> getMessageVibrateToggle() {
    return messageVibrateToggle;
  }

  void setHasCustomNotifications(boolean hasCustomNotifications) {
    repository.setHasCustomNotifications(hasCustomNotifications);
  }

  void setMessageVibrate(@NonNull RecipientDatabase.VibrateState vibrateState) {
    repository.setMessageVibrate(vibrateState);
  }

  void setMessageSound(@Nullable Uri sound) {
    repository.setMessageSound(sound);
  }

  void setCallSound(@Nullable Uri sound) {
    repository.setCallSound(sound);
  }

  LiveData<Boolean> getShowCallingOptions() {
    return showCallingOptions;
  }

  LiveData<Uri> getRingtone() {
    return ringtone;
  }

  LiveData<RecipientDatabase.VibrateState> getCallingVibrateState() {
    return callingVibrateState;
  }

  void setCallingVibrate(@NonNull RecipientDatabase.VibrateState vibrateState) {
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
