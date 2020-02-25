package org.thoughtcrime.securesms.conversation;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.database.ContentObserver;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.thoughtcrime.securesms.database.CursorList;
import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.stickers.StickerSearchRepository;
import org.thoughtcrime.securesms.util.CloseableLiveData;
import org.thoughtcrime.securesms.util.Throttler;

class ConversationStickerViewModel extends ViewModel {

  private static final int SEARCH_LIMIT = 10;

  private final Application                                  application;
  private final StickerSearchRepository                      repository;
  private final CloseableLiveData<CursorList<StickerRecord>> stickers;
  private final MutableLiveData<Boolean>                     stickersAvailable;
  private final Throttler                                    availabilityThrottler;
  private final ContentObserver                              packObserver;

  private ConversationStickerViewModel(@NonNull Application application, @NonNull StickerSearchRepository repository) {
    this.application           = application;
    this.repository            = repository;
    this.stickers              = new CloseableLiveData<>();
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

  @NonNull LiveData<CursorList<StickerRecord>> getStickerResults() {
    return stickers;
  }

  @NonNull LiveData<Boolean> getStickersAvailability() {
    repository.getStickerFeatureAvailability(stickersAvailable::postValue);
    return stickersAvailable;
  }

  void onInputTextUpdated(@NonNull String text) {
    if (TextUtils.isEmpty(text) || text.length() > SEARCH_LIMIT) {
      stickers.setValue(CursorList.emptyList());
    } else {
      repository.searchByEmoji(text, stickers::postValue);
    }
  }

  @Override
  protected void onCleared() {
    stickers.close();
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
      return modelClass.cast(new ConversationStickerViewModel(application, repository));
    }
  }
}
