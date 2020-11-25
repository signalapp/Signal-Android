package org.thoughtcrime.securesms.mediasend;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Manages the observable datasets available in {@link MediaSendActivity}.
 */
class MediaSendViewModel extends ViewModel {

  private static final String TAG = MediaSendViewModel.class.getSimpleName();

  private static final int MAX_PUSH = 32;
  private static final int MAX_SMS  = 1;

  private final Application                        application;
  private final MediaRepository                    repository;
  private final MutableLiveData<List<Media>>       selectedMedia;
  private final MutableLiveData<List<Media>>       bucketMedia;
  private final MutableLiveData<Integer>           position;
  private final MutableLiveData<String>            bucketId;
  private final MutableLiveData<List<MediaFolder>> folders;
  private final MutableLiveData<CountButtonState>  countButtonState;
  private final MutableLiveData<Boolean>           cameraButtonVisibility;
  private final SingleLiveEvent<Error>             error;
  private final Map<Uri, Object>                   savedDrawState;

  private MediaConstraints            mediaConstraints;
  private CharSequence                body;
  private CountButtonState.Visibility countButtonVisibility;
  private boolean                     sentMedia;
  private Optional<Media>             lastImageCapture;
  private int                         maxSelection;

  private MediaSendViewModel(@NonNull Application application, @NonNull MediaRepository repository) {
    this.application            = application;
    this.repository             = repository;
    this.selectedMedia          = new MutableLiveData<>();
    this.bucketMedia            = new MutableLiveData<>();
    this.position               = new MutableLiveData<>();
    this.bucketId               = new MutableLiveData<>();
    this.folders                = new MutableLiveData<>();
    this.countButtonState       = new MutableLiveData<>();
    this.cameraButtonVisibility = new MutableLiveData<>();
    this.error                  = new SingleLiveEvent<>();
    this.savedDrawState         = new HashMap<>();
    this.countButtonVisibility  = CountButtonState.Visibility.FORCED_OFF;
    this.lastImageCapture       = Optional.absent();
    this.body                   = "";

    position.setValue(-1);
    countButtonState.setValue(new CountButtonState(0, countButtonVisibility));
    cameraButtonVisibility.setValue(false);
  }

  void setTransport(@NonNull TransportOption transport) {
    if (transport.isSms()) {
      maxSelection     = MAX_SMS;
      mediaConstraints = MediaConstraints.getMmsMediaConstraints(transport.getSimSubscriptionId().or(-1));
    } else {
      maxSelection     = MAX_PUSH;
      mediaConstraints = MediaConstraints.getPushMediaConstraints();
    }
  }

  void onSelectedMediaChanged(@NonNull Context context, @NonNull List<Media> newMedia) {
    repository.getPopulatedMedia(context, newMedia, populatedMedia -> {
      Util.runOnMain(() -> {

        List<Media> filteredMedia = getFilteredMedia(context, populatedMedia, mediaConstraints);

        if (filteredMedia.size() != newMedia.size()) {
          error.setValue(Error.ITEM_TOO_LARGE);
        } else if (filteredMedia.size() > maxSelection) {
          filteredMedia = filteredMedia.subList(0, maxSelection);
          error.setValue(Error.TOO_MANY_ITEMS);
        }

        if (filteredMedia.size() > 0) {
          String computedId = Stream.of(filteredMedia)
                                    .skip(1)
                                    .reduce(filteredMedia.get(0).getBucketId().or(Media.ALL_MEDIA_BUCKET_ID), (id, m) -> {
                                      if (Util.equals(id, m.getBucketId().or(Media.ALL_MEDIA_BUCKET_ID))) {
                                        return id;
                                      } else {
                                        return Media.ALL_MEDIA_BUCKET_ID;
                                      }
                                    });
          bucketId.setValue(computedId);
        } else {
          bucketId.setValue(Media.ALL_MEDIA_BUCKET_ID);
          countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;
        }

        selectedMedia.setValue(filteredMedia);
        countButtonState.setValue(new CountButtonState(filteredMedia.size(), countButtonVisibility));
      });
    });
  }

  void onSingleMediaSelected(@NonNull Context context, @NonNull Media media) {
    repository.getPopulatedMedia(context, Collections.singletonList(media), populatedMedia -> {
      Util.runOnMain(() -> {
        List<Media> filteredMedia = getFilteredMedia(context, populatedMedia, mediaConstraints);

        if (filteredMedia.isEmpty()) {
          error.setValue(Error.ITEM_TOO_LARGE);
          bucketId.setValue(Media.ALL_MEDIA_BUCKET_ID);
        } else {
          bucketId.setValue(filteredMedia.get(0).getBucketId().or(Media.ALL_MEDIA_BUCKET_ID));
        }

        countButtonVisibility = CountButtonState.Visibility.FORCED_OFF;

        selectedMedia.setValue(filteredMedia);
        countButtonState.setValue(new CountButtonState(filteredMedia.size(), countButtonVisibility));
      });
    });
  }

  void onMultiSelectStarted() {
    countButtonVisibility = CountButtonState.Visibility.FORCED_ON;
    countButtonState.setValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
  }

  void onImageEditorStarted() {
    countButtonVisibility = CountButtonState.Visibility.FORCED_OFF;
    countButtonState.setValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
    cameraButtonVisibility.setValue(false);
  }

  void onCameraStarted() {
    countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;
    countButtonState.setValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
    cameraButtonVisibility.setValue(false);
  }

  void onItemPickerStarted() {
    countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;
    countButtonState.setValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
    cameraButtonVisibility.setValue(true);
  }

  void onFolderPickerStarted() {
    countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;
    countButtonState.setValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
    cameraButtonVisibility.setValue(true);
  }

  void onBodyChanged(@NonNull CharSequence body) {
    this.body = body;
  }

  void onFolderSelected(@NonNull String bucketId) {
    this.bucketId.setValue(bucketId);
    bucketMedia.setValue(Collections.emptyList());
  }

  void onPageChanged(int position) {
    if (position < 0 || position >= getSelectedMediaOrDefault().size()) {
      Log.w(TAG, "Tried to move to an out-of-bounds item. Size: " + getSelectedMediaOrDefault().size() + ", position: " + position);
      return;
    }

    this.position.setValue(position);
  }

  void onMediaItemRemoved(@NonNull Context context, int position) {
    if (position < 0 || position >= getSelectedMediaOrDefault().size()) {
      Log.w(TAG, "Tried to remove an out-of-bounds item. Size: " + getSelectedMediaOrDefault().size() + ", position: " + position);
      return;
    }

    Media removed = getSelectedMediaOrDefault().remove(position);

    if (removed != null && BlobProvider.isAuthority(removed.getUri())) {
      BlobProvider.getInstance().delete(context, removed.getUri());
    }

    selectedMedia.setValue(selectedMedia.getValue());
  }

  void onImageCaptured(@NonNull Media media) {
    List<Media> selected = selectedMedia.getValue();

    if (selected == null) {
      selected = new LinkedList<>();
    }

    if (selected.size() >= maxSelection) {
      error.setValue(Error.TOO_MANY_ITEMS);
      return;
    }

    lastImageCapture = Optional.of(media);

    selected.add(media);
    selectedMedia.setValue(selected);
    position.setValue(selected.size() - 1);
    bucketId.setValue(Media.ALL_MEDIA_BUCKET_ID);

    if (selected.size() == 1) {
      countButtonVisibility = CountButtonState.Visibility.FORCED_OFF;
    } else {
      countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;
    }

    countButtonState.setValue(new CountButtonState(selected.size(), countButtonVisibility));
  }

  void onImageCaptureUndo(@NonNull Context context) {
    List<Media> selected = getSelectedMediaOrDefault();

    if (lastImageCapture.isPresent() && selected.contains(lastImageCapture.get()) && selected.size() == 1) {
      selected.remove(lastImageCapture.get());
      selectedMedia.setValue(selected);
      countButtonState.setValue(new CountButtonState(selected.size(), countButtonVisibility));
      BlobProvider.getInstance().delete(context, lastImageCapture.get().getUri());
    }
  }


  void onCaptionChanged(@NonNull String newCaption) {
    if (position.getValue() >= 0 && !Util.isEmpty(selectedMedia.getValue())) {
      selectedMedia.getValue().get(position.getValue()).setCaption(TextUtils.isEmpty(newCaption) ? null : newCaption);
    }
  }

  void saveDrawState(@NonNull Map<Uri, Object> state) {
    savedDrawState.clear();
    savedDrawState.putAll(state);
  }

  void onSendClicked() {
    sentMedia = true;
  }

  @NonNull Map<Uri, Object> getDrawState() {
    return savedDrawState;
  }

  @NonNull LiveData<List<Media>> getSelectedMedia() {
    return selectedMedia;
  }

  @NonNull LiveData<List<Media>> getMediaInBucket(@NonNull Context context, @NonNull String bucketId) {
    repository.getMediaInBucket(context, bucketId, bucketMedia::postValue);
    return bucketMedia;
  }

  @NonNull LiveData<List<MediaFolder>> getFolders(@NonNull Context context) {
    repository.getFolders(context, folders::postValue);
    return folders;
  }

  @NonNull LiveData<CountButtonState> getCountButtonState() {
    return countButtonState;
  }

  @NonNull LiveData<Boolean> getCameraButtonVisibility() {
    return cameraButtonVisibility;
  }

  @NonNull CharSequence getBody() {
    return body;
  }

  @NonNull LiveData<Integer> getPosition() {
    return position;
  }

  @NonNull LiveData<String> getBucketId() {
    return bucketId;
  }

  @NonNull LiveData<Error> getError() {
    return error;
  }

  int getMaxSelection() {
    return maxSelection;
  }

  private @NonNull List<Media> getSelectedMediaOrDefault() {
    return selectedMedia.getValue() == null ? Collections.emptyList()
                                            : selectedMedia.getValue();
  }

  private @NonNull List<Media> getFilteredMedia(@NonNull Context context, @NonNull List<Media> media, @NonNull MediaConstraints mediaConstraints) {
    return Stream.of(media).filter(m -> MediaUtil.isGif(m.getMimeType())       ||
                                        MediaUtil.isImageType(m.getMimeType()) ||
                                        MediaUtil.isVideoType(m.getMimeType()))
                           .filter(m -> {
                             return (MediaUtil.isImageType(m.getMimeType()) && !MediaUtil.isGif(m.getMimeType()))               ||
                                    (MediaUtil.isGif(m.getMimeType()) && m.getSize() < mediaConstraints.getGifMaxSize(context)) ||
                                    (MediaUtil.isVideoType(m.getMimeType()) && m.getSize() < mediaConstraints.getVideoMaxSize(context));
                           }).toList();

  }

  @Override
  protected void onCleared() {
    if (!sentMedia) {
      Stream.of(getSelectedMediaOrDefault())
            .map(Media::getUri)
            .filter(BlobProvider::isAuthority)
            .forEach(uri -> BlobProvider.getInstance().delete(application.getApplicationContext(), uri));
    }
  }

  enum Error {
    ITEM_TOO_LARGE, TOO_MANY_ITEMS
  }

  static class CountButtonState {
    private final int        count;
    private final Visibility visibility;

    private CountButtonState(int count, @NonNull Visibility visibility) {
      this.count      = count;
      this.visibility = visibility;
    }

    int getCount() {
      return count;
    }

    boolean isVisible() {
      switch (visibility) {
        case FORCED_ON:   return true;
        case FORCED_OFF:  return false;
        case CONDITIONAL: return count > 0;
        default:          return false;
      }
    }

    enum Visibility {
      CONDITIONAL, FORCED_ON, FORCED_OFF
    }
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final Application     application;
    private final MediaRepository repository;

    Factory(@NonNull Application application, @NonNull MediaRepository repository) {
      this.application = application;
      this.repository  = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new MediaSendViewModel(application, repository));
    }
  }
}
