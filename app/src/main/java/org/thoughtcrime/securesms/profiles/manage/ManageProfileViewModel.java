package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

class ManageProfileViewModel extends ViewModel {

  private static final String TAG = Log.tag(ManageProfileViewModel.class);

  private final MutableLiveData<AvatarState> avatar;
  private final MutableLiveData<ProfileName> profileName;
  private final MutableLiveData<String>      username;
  private final MutableLiveData<String>      about;
  private final MutableLiveData<String>      aboutEmoji;
  private final SingleLiveEvent<Event>       events;
  private final RecipientForeverObserver     observer;

  public ManageProfileViewModel() {
    this.avatar      = new MutableLiveData<>();
    this.profileName = new MutableLiveData<>();
    this.username    = new MutableLiveData<>();
    this.about       = new MutableLiveData<>();
    this.aboutEmoji  = new MutableLiveData<>();
    this.events      = new SingleLiveEvent<>();
    this.observer    = this::onRecipientChanged;

    SignalExecutors.BOUNDED.execute(() -> {
      onRecipientChanged(Recipient.self().fresh());

      StreamDetails details = AvatarHelper.getSelfProfileAvatarStream(ApplicationDependencies.getApplication());
      if (details != null) {
        try {
          avatar.postValue(AvatarState.loaded(StreamUtil.readFully(details.getStream())));
        } catch (IOException e) {
          Log.w(TAG, "Failed to read avatar!");
          avatar.postValue(AvatarState.none());
        }
      } else {
        avatar.postValue(AvatarState.none());
      }

      ApplicationDependencies.getJobManager().add(RetrieveProfileJob.forRecipient(Recipient.self().getId()));
    });

    Recipient.self().live().observeForever(observer);
  }

  public @NonNull LiveData<AvatarState> getAvatar() {
    return avatar;
  }

  public @NonNull LiveData<ProfileName> getProfileName() {
    return profileName;
  }

  public @NonNull LiveData<String> getUsername() {
    return username;
  }

  public @NonNull LiveData<String> getAbout() {
    return about;
  }

  public @NonNull LiveData<String> getAboutEmoji() {
    return aboutEmoji;
  }

  public @NonNull LiveData<Event> getEvents() {
    return events;
  }

  public boolean shouldShowUsername() {
    return FeatureFlags.usernames();
  }

  public void onAvatarSelected(@NonNull Context context, @Nullable Media media) {
    if (media == null) {
      SignalExecutors.BOUNDED.execute(() -> {
        AvatarHelper.delete(context, Recipient.self().getId());
        avatar.postValue(AvatarState.none());
        ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
      });
    } else {
      SignalExecutors.BOUNDED.execute(() -> {
        try {
          InputStream stream = BlobProvider.getInstance().getStream(context, media.getUri());
          byte[]      data   = StreamUtil.readFully(stream);

          AvatarHelper.setAvatar(context, Recipient.self().getId(), new ByteArrayInputStream(data));
          avatar.postValue(AvatarState.loaded(data));

          ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
        } catch (IOException e) {
          Log.w(TAG, "Failed to save avatar!", e);
          events.postValue(Event.AVATAR_FAILURE);
        }
      });
    }
  }

  public boolean canRemoveAvatar() {
    return avatar.getValue() != null;
  }

  private void onRecipientChanged(@NonNull Recipient recipient) {
    profileName.postValue(recipient.getProfileName());
    username.postValue(recipient.getUsername().orNull());
    about.postValue(recipient.getAbout());
    aboutEmoji.postValue(recipient.getAboutEmoji());
  }

  @Override
  protected void onCleared() {
    Recipient.self().live().removeForeverObserver(observer);
  }

  public static class AvatarState {
    private final byte[]       avatar;
    private final LoadingState loadingState;

    public AvatarState(@Nullable byte[] avatar, @NonNull LoadingState loadingState) {
      this.avatar       = avatar;
      this.loadingState = loadingState;
    }

    private static @NonNull AvatarState none() {
      return new AvatarState(null, LoadingState.LOADED);
    }

    private static @NonNull AvatarState loaded(@Nullable byte[] avatar) {
      return new AvatarState(avatar, LoadingState.LOADED);
    }

    private static @NonNull AvatarState loading(@Nullable byte[] avatar) {
      return new AvatarState(avatar, LoadingState.LOADING);
    }

    public @Nullable byte[] getAvatar() {
      return avatar;
    }

    public LoadingState getLoadingState() {
      return loadingState;
    }
  }

  public enum LoadingState {
    LOADING, LOADED
  }

  enum Event {
    AVATAR_FAILURE
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ManageProfileViewModel()));
    }
  }

}
