package org.thoughtcrime.securesms.wallpaper.crop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.imageeditor.core.model.EditorModel;
import org.thoughtcrime.securesms.fonts.FontTypefaceProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;

import java.io.IOException;
import java.util.Objects;

final class WallpaperCropViewModel extends ViewModel {

  private static final String TAG = Log.tag(WallpaperCropViewModel.class);

  private final @NonNull WallpaperCropRepository  repository;
  private final @NonNull MutableLiveData<Boolean> blur;
  private final @NonNull LiveData<Recipient>      recipient;

  public WallpaperCropViewModel(@Nullable RecipientId recipientId,
                                @NonNull WallpaperCropRepository repository)
  {
    this.repository = repository;
    this.blur       = new MutableLiveData<>(false);
    this.recipient  = recipientId != null ? Recipient.live(recipientId).getLiveData() : LiveDataUtil.just(Recipient.UNKNOWN);
  }

  void render(@NonNull Context context,
              @NonNull EditorModel model,
              @NonNull Point size,
              @NonNull AsynchronousCallback.WorkerThread<ChatWallpaper, Error> callback)
  {
    SignalExecutors.BOUNDED.execute(
            () -> {
              Bitmap bitmap = model.render(context, size, new FontTypefaceProvider());
              try {
                ChatWallpaper chatWallpaper = repository.setWallPaper(BitmapUtil.toWebPByteArray(bitmap));
                callback.onComplete(chatWallpaper);
              } catch (IOException e) {
                Log.w(TAG, e);
                callback.onError(Error.SAVING);
              } finally {
                bitmap.recycle();
              }
            });
  }

  LiveData<Boolean> getBlur() {
    return Transformations.distinctUntilChanged(blur);
  }

  LiveData<Recipient> getRecipient() {
    return recipient;
  }

  @MainThread
  void setBlur(boolean blur) {
    this.blur.setValue(blur);
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final RecipientId recipientId;

    public Factory(@Nullable RecipientId recipientId) {
      this.recipientId = recipientId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {

      WallpaperCropRepository wallpaperCropRepository = new WallpaperCropRepository(recipientId);

      return Objects.requireNonNull(modelClass.cast(new WallpaperCropViewModel(recipientId, wallpaperCropRepository)));
    }
  }

  enum Error {
    SAVING
  }
}
