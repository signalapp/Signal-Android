package org.thoughtcrime.securesms.scribbles;

import android.app.Application;
import android.database.ContentObserver;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.stickers.StickerSearchRepository;
import org.thoughtcrime.securesms.util.Throttler;

public final class ImageEditorFragmentViewModel extends ViewModel {

  private final Application              application;
  private final StickerSearchRepository  repository;
  private final MutableLiveData<Boolean> stickersAvailable;
  private final Throttler                availabilityThrottler;
  private final ContentObserver          packObserver;

  private ImageEditorFragmentViewModel(@NonNull Application application, @NonNull StickerSearchRepository repository) {
    this.application           = application;
    this.repository            = repository;
    this.stickersAvailable     = new MutableLiveData<>();
    this.availabilityThrottler = new Throttler(500);
    this.packObserver          = new ContentObserver(new Handler()) {
      @Override
      public void onChange(boolean selfChange) {
        availabilityThrottler.publish(() -> repository.getStickerFeatureAvailability(stickersAvailable::postValue));
      }
    };

    application.getContentResolver().registerContentObserver(DatabaseContentProviders.StickerPack.CONTENT_URI, true, packObserver);
  }

  @NonNull LiveData<Boolean> getStickersAvailability() {
    repository.getStickerFeatureAvailability(stickersAvailable::postValue);
    return stickersAvailable;
  }

  @Override
  protected void onCleared() {
    application.getContentResolver().unregisterContentObserver(packObserver);
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    private final Application             application;
    private final StickerSearchRepository repository;

    public Factory(@NonNull Application application, @NonNull StickerSearchRepository repository) {
      this.application = application;
      this.repository  = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ImageEditorFragmentViewModel(application, repository));
    }
  }
}
