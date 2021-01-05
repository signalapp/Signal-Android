package org.thoughtcrime.securesms.stickers;

import android.app.Application;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.util.Throttler;

import java.util.List;

final class StickerKeyboardPageViewModel extends ViewModel {

  static final String RECENT_PACK_ID = "RECENT";

  private final Application                          application;
  private final StickerKeyboardRepository            repository;
  private final MutableLiveData<List<StickerRecord>> stickers;
  private final Throttler                            observerThrottler;
  private final ContentObserver                      observer;

  private String packId;

  private StickerKeyboardPageViewModel(@NonNull Application application, @NonNull StickerKeyboardRepository repository) {
    this.application       = application;
    this.repository        = repository;
    this.stickers          = new MutableLiveData<>();
    this.observerThrottler = new Throttler(500);
    this.observer          = new ContentObserver(new Handler(Looper.getMainLooper())) {
      @Override
      public void onChange(boolean selfChange) {
        observerThrottler.publish(() -> getStickers(packId));
      }
    };

    application.getContentResolver().registerContentObserver(DatabaseContentProviders.Sticker.CONTENT_URI, true, observer);
  }

  LiveData<List<StickerRecord>> getStickers(@NonNull String packId) {
    this.packId = packId;

    if (RECENT_PACK_ID.equals(packId)) {
      repository.getRecentStickers(stickers::postValue);
    } else {
      repository.getStickersForPack(packId, stickers::postValue);
    }

    return stickers;
  }

  @Override
  protected void onCleared() {
    application.getContentResolver().unregisterContentObserver(observer);
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    private final Application               application;
    private final StickerKeyboardRepository repository;

    Factory(@NonNull Application application, @NonNull StickerKeyboardRepository repository) {
      this.application = application;
      this.repository  = repository;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new StickerKeyboardPageViewModel(application, repository));
    }
  }
}
