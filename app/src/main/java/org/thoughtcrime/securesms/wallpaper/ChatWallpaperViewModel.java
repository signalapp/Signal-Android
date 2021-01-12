package org.thoughtcrime.securesms.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.MappingModel;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Objects;

public class ChatWallpaperViewModel extends ViewModel {

  private final ChatWallpaperRepository                  repository = new ChatWallpaperRepository();
  private final MutableLiveData<Optional<ChatWallpaper>> wallpaper  = new MutableLiveData<>();
  private final MutableLiveData<List<ChatWallpaper>>     builtins   = new MutableLiveData<>();
  private final RecipientId                              recipientId;

  private ChatWallpaperViewModel(@Nullable RecipientId recipientId) {
    this.recipientId = recipientId;

    repository.getAllWallpaper(builtins::postValue);
  }

  void setWallpaper(@Nullable ChatWallpaper chatWallpaper) {
    wallpaper.setValue(Optional.fromNullable(chatWallpaper));
  }

  void saveWallpaperSelection(@NonNull ChatWallpaper selected) {
    // TODO
  }

  public @Nullable RecipientId getRecipientId() {
    return recipientId;
  }

  LiveData<Optional<ChatWallpaper>> getCurrentWallpaper() {
    return wallpaper;
  }

  LiveData<List<MappingModel<?>>> getWallpapers() {
    return Transformations.map(Transformations.distinctUntilChanged(builtins),
                               wallpapers -> Stream.of(wallpapers).<MappingModel<?>>map(ChatWallpaperSelectionMappingModel::new).toList());
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
