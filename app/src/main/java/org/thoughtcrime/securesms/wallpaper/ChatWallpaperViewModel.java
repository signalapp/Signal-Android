package org.thoughtcrime.securesms.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Objects;

public class ChatWallpaperViewModel extends ViewModel {

  private final ChatWallpaperRepository                  repository    = new ChatWallpaperRepository();
  private final MutableLiveData<Optional<ChatWallpaper>> wallpaper     = new MutableLiveData<>();
  private final MutableLiveData<List<ChatWallpaper>>     builtins      = new MutableLiveData<>();
  private final MutableLiveData<Boolean> dimInDarkTheme = new MutableLiveData<>();
  private final RecipientId                              recipientId;

  private ChatWallpaperViewModel(@Nullable RecipientId recipientId) {
    this.recipientId = recipientId;

    ChatWallpaper currentWallpaper = repository.getCurrentWallpaper(recipientId);
    dimInDarkTheme.setValue(currentWallpaper != null && currentWallpaper.getDimLevelForDarkTheme() > 0f);
    wallpaper.setValue(Optional.fromNullable(currentWallpaper));
    repository.getAllWallpaper(builtins::postValue);
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
      repository.saveWallpaper(recipientId, null);
      return;
    }

    Optional<ChatWallpaper> updated = wallpaper.transform(paper -> ChatWallpaperFactory.updateWithDimming(paper, dimInDarkTheme ? ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME : 0f));

    if (updated.isPresent()) {
      repository.saveWallpaper(recipientId, updated.get());
    }
  }

  void resetAllWallpaper() {
    repository.resetAllWallpaper();
  }

  public @Nullable RecipientId getRecipientId() {
    return recipientId;
  }

  LiveData<Optional<ChatWallpaper>> getCurrentWallpaper() {
    return wallpaper;
  }

  LiveData<List<MappingModel<?>>> getWallpapers() {
    return LiveDataUtil.combineLatest(builtins, dimInDarkTheme, (wallpapers, dimInDarkMode) ->
      Stream.of(wallpapers)
            .map(paper -> ChatWallpaperFactory.updateWithDimming(paper, dimInDarkMode ? 1f : 0f))
            .<MappingModel<?>>map(ChatWallpaperSelectionMappingModel::new).toList()
    );
  }

  LiveData<Boolean> getDimInDarkTheme() {
    return dimInDarkTheme;
  }

  boolean isGlobal() {
    return recipientId == null;
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
