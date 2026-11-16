package org.thoughtcrime.securesms.giph.mp4;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.giph.model.GiphyImage;
import org.thoughtcrime.securesms.mediakeyboard.SignalGifKeyboardRepository;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

/**
 * ViewModel which saves a chosen gif to a blob. This is to be bound to the activity, and also
 * holds the gif repository GiphyMp4Fragment searches, so a pick can be resolved back to its
 * GiphyImage after a configuration change.
 */
public final class GiphyMp4ViewModel extends ViewModel {

  private final GiphyMp4Repository                  repository;
  private final SingleLiveEvent<GiphyMp4SaveResult> saveResultEvents;

  private SignalGifKeyboardRepository gifKeyboardRepository;

  public GiphyMp4ViewModel() {
    this.repository       = new GiphyMp4Repository();
    this.saveResultEvents = new SingleLiveEvent<>();
  }

  public @NonNull SignalGifKeyboardRepository getGifKeyboardRepository() {
    if (gifKeyboardRepository == null) {
      gifKeyboardRepository = new SignalGifKeyboardRepository();
    }
    return gifKeyboardRepository;
  }

  public void saveToBlob(@NonNull GiphyImage giphyImage) {
    saveResultEvents.postValue(new GiphyMp4SaveResult.InProgress());
    repository.saveToBlob(giphyImage, saveResultEvents::postValue);
  }

  public @NonNull LiveData<GiphyMp4SaveResult> getSaveResultEvents() {
    return saveResultEvents;
  }
}
