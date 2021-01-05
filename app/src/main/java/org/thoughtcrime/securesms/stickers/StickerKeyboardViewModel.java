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
import org.thoughtcrime.securesms.stickers.StickerKeyboardRepository.PackListResult;
import org.thoughtcrime.securesms.util.Throttler;

final class StickerKeyboardViewModel extends ViewModel {

  private final Application                     application;
  private final MutableLiveData<PackListResult> packs;
  private final Throttler                       observerThrottler;
  private final ContentObserver                 observer;

  private StickerKeyboardViewModel(@NonNull Application application, @NonNull StickerKeyboardRepository repository) {
    this.application       = application;
    this.packs             = new MutableLiveData<>();
    this.observerThrottler = new Throttler(500);
    this.observer          = new ContentObserver(new Handler(Looper.getMainLooper())) {
      @Override
      public void onChange(boolean selfChange) {
        observerThrottler.publish(() -> repository.getPackList(packs::postValue));
      }
    };

    repository.getPackList(packs::postValue);
    application.getContentResolver().registerContentObserver(DatabaseContentProviders.StickerPack.CONTENT_URI, true, observer);
  }

  @NonNull LiveData<PackListResult> getPacks() {
    return packs;
  }

  @Override
  protected void onCleared() {
    application.getContentResolver().unregisterContentObserver(observer);
  }

  public static final class Factory extends ViewModelProvider.NewInstanceFactory {
    private final Application               application;
    private final StickerKeyboardRepository repository;

    public Factory(@NonNull Application application, @NonNull StickerKeyboardRepository repository) {
      this.application = application;
      this.repository  = repository;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new StickerKeyboardViewModel(application, repository));
    }
  }
}
