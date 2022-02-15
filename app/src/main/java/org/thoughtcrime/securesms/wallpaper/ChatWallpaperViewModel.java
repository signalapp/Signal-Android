package org.thoughtcrime.securesms.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Objects;

public class ChatWallpaperViewModel extends ViewModel {

  private final ChatWallpaperRepository                  repository              = new ChatWallpaperRepository();
  private final MutableLiveData<Optional<ChatWallpaper>> wallpaper               = new MutableLiveData<>();
  private final MutableLiveData<List<ChatWallpaper>>     builtins                = new MutableLiveData<>();
  private final MutableLiveData<Boolean>                 dimInDarkTheme          = new MutableLiveData<>();
  private final MutableLiveData<Boolean>                 enableWallpaperControls = new MutableLiveData<>();
  private final MutableLiveData<ChatColors>              chatColors              = new MutableLiveData<>();
  private final RecipientId                              recipientId;
  private final LiveRecipient                            liveRecipient;
  private final RecipientForeverObserver                 recipientObserver       = r -> refreshChatColors();
  private final LiveData<WallpaperPreviewPortrait>       wallpaperPreviewPortrait;

  private ChatWallpaperViewModel(@Nullable RecipientId recipientId) {
    this.recipientId = recipientId;

    ChatWallpaper currentWallpaper = repository.getCurrentWallpaper(recipientId);
    dimInDarkTheme.setValue(currentWallpaper == null || currentWallpaper.getDimLevelForDarkTheme() > 0f);
    enableWallpaperControls.setValue(hasClearableWallpaper());
    wallpaper.setValue(Optional.fromNullable(currentWallpaper));

    if (recipientId != null) {
      liveRecipient = Recipient.live(recipientId);
      liveRecipient.observeForever(recipientObserver);
      wallpaperPreviewPortrait = Transformations.map(liveRecipient.getLiveData(), recipient -> {
        if (recipient.getContactPhoto() != null) {
          return new WallpaperPreviewPortrait.ContactPhoto(recipient);
        } else {
          return new WallpaperPreviewPortrait.SolidColor(recipient.getAvatarColor());
        }
      });
    } else {
      liveRecipient            = null;
      wallpaperPreviewPortrait = new DefaultValueLiveData<>(new WallpaperPreviewPortrait.SolidColor(AvatarColor.A100));
    }
  }

  @Override
  protected void onCleared() {
    if (liveRecipient != null) {
      liveRecipient.removeForeverObserver(recipientObserver);
    }
  }

  void refreshWallpaper() {
    repository.getAllWallpaper(builtins::postValue);
  }

  void refreshChatColors() {
    chatColors.postValue(repository.getCurrentChatColors(recipientId));
  }

  void setDimInDarkTheme(boolean shouldDimInDarkTheme) {
    dimInDarkTheme.setValue(shouldDimInDarkTheme);

    Optional<ChatWallpaper> wallpaper = this.wallpaper.getValue();
    if (wallpaper.isPresent()) {
      repository.setDimInDarkTheme(recipientId, shouldDimInDarkTheme);
    }
  }

  void setWallpaper(@Nullable ChatWallpaper chatWallpaper) {
    wallpaper.setValue(Optional.fromNullable(chatWallpaper));
  }

  void saveWallpaperSelection() {
    Optional<ChatWallpaper> wallpaper      = this.wallpaper.getValue();
    boolean                 dimInDarkTheme = this.dimInDarkTheme.getValue();

    if (!wallpaper.isPresent()) {
      repository.saveWallpaper(recipientId, null, this::refreshChatColors);

      if (recipientId != null) {
        ChatWallpaper globalWallpaper = SignalStore.wallpaper().getWallpaper();

        this.wallpaper.setValue(Optional.fromNullable(globalWallpaper));
        this.dimInDarkTheme.setValue(globalWallpaper == null || globalWallpaper.getDimLevelForDarkTheme() > 0);
      }

      enableWallpaperControls.setValue(false);
      return;
    } else {
      enableWallpaperControls.setValue(true);
    }

    Optional<ChatWallpaper> updated = wallpaper.transform(paper -> ChatWallpaperFactory.updateWithDimming(paper, dimInDarkTheme ? ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME : 0f));

    if (updated.isPresent()) {
      repository.saveWallpaper(recipientId, updated.get(), this::refreshChatColors);
    }
  }

  void resetAllWallpaper() {
    repository.resetAllWallpaper(this::refreshChatColors);
  }

  @Nullable RecipientId getRecipientId() {
    return recipientId;
  }

  @NonNull LiveData<Optional<ChatWallpaper>> getCurrentWallpaper() {
    return wallpaper;
  }

  @NonNull LiveData<ChatColors> getCurrentChatColors() {
    return chatColors;
  }

  @NonNull LiveData<WallpaperPreviewPortrait> getWallpaperPreviewPortrait() {
    return wallpaperPreviewPortrait;
  }

  @NonNull LiveData<List<MappingModel<?>>> getWallpapers() {
    return LiveDataUtil.combineLatest(builtins, dimInDarkTheme, (wallpapers, dimInDarkMode) ->
        Stream.of(wallpapers)
              .map(paper -> ChatWallpaperFactory.updateWithDimming(paper, dimInDarkMode ? ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME : 0f))
            .<MappingModel<?>>map(ChatWallpaperSelectionMappingModel::new).toList()
    );
  }

  @NonNull LiveData<Boolean> getDimInDarkTheme() {
    return dimInDarkTheme;
  }

  @NonNull LiveData<Boolean> getEnableWallpaperControls() {
    return enableWallpaperControls;
  }

  boolean isGlobal() {
    return recipientId == null;
  }

  void clearChatColor() {
    repository.clearChatColor(recipientId, this::refreshChatColors);
  }

  private boolean hasClearableWallpaper() {
    return (isGlobal() && SignalStore.wallpaper().hasWallpaperSet()) ||
           (recipientId != null && Recipient.live(recipientId).get().hasOwnWallpaper());
  }

  public void resetAllChatColors() {
    repository.resetAllChatColors(this::refreshChatColors);
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final RecipientId recipientId;

    public Factory(@Nullable RecipientId recipientId) {
      this.recipientId = recipientId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ChatWallpaperViewModel(recipientId)));
    }
  }
}
