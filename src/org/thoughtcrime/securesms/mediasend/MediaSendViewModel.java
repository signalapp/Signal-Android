package org.thoughtcrime.securesms.mediasend;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the observable datasets available in {@link MediaSendActivity}.
 */
class MediaSendViewModel extends ViewModel {

  private final MediaRepository                    repository;
  private final MutableLiveData<List<Media>>       selectedMedia;
  private final MutableLiveData<List<Media>>       bucketMedia;
  private final MutableLiveData<Integer>           position;
  private final MutableLiveData<String>            bucketId;
  private final MutableLiveData<List<MediaFolder>> folders;
  private final MutableLiveData<CountButtonState>  countButtonState;
  private final SingleLiveEvent<Error>             error;
  private final Map<Uri, Object>                   savedDrawState;

  private MediaConstraints            mediaConstraints;
  private CharSequence                body;
  private CountButtonState.Visibility countButtonVisibility;

  private MediaSendViewModel(@NonNull MediaRepository repository) {
    this.repository            = repository;
    this.selectedMedia         = new MutableLiveData<>();
    this.bucketMedia           = new MutableLiveData<>();
    this.position              = new MutableLiveData<>();
    this.bucketId              = new MutableLiveData<>();
    this.folders               = new MutableLiveData<>();
    this.countButtonState      = new MutableLiveData<>();
    this.error                 = new SingleLiveEvent<>();
    this.savedDrawState        = new HashMap<>();
    this.countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;

    position.setValue(-1);
    countButtonState.setValue(new CountButtonState(0, CountButtonState.Visibility.CONDITIONAL));
  }

  void setMediaConstraints(@NonNull MediaConstraints mediaConstraints) {
    this.mediaConstraints = mediaConstraints;
  }

  void onSelectedMediaChanged(@NonNull Context context, @NonNull List<Media> newMedia) {
    repository.getPopulatedMedia(context, newMedia, populatedMedia -> {
      List<Media> filteredMedia = getFilteredMedia(context, populatedMedia, mediaConstraints);

      if (filteredMedia.size() != newMedia.size()) {
        error.postValue(Error.ITEM_TOO_LARGE);
      }

      if (filteredMedia.size() > 0) {
        String computedId = Stream.of(filteredMedia)
                                  .skip(1)
                                  .reduce(filteredMedia.get(0).getBucketId().orNull(), (id, m) -> {
                                    if (Util.equals(id, m.getBucketId().orNull())) {
                                      return id;
                                    } else {
                                      return Media.ALL_MEDIA_BUCKET_ID;
                                    }
                                  });
        bucketId.postValue(computedId);
      } else {
        bucketId.postValue(Media.ALL_MEDIA_BUCKET_ID);
        countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;
      }

      selectedMedia.postValue(filteredMedia);
      countButtonState.postValue(new CountButtonState(filteredMedia.size(), countButtonVisibility));
    });
  }

  void onMultiSelectStarted() {
    countButtonVisibility = CountButtonState.Visibility.FORCED_ON;
    countButtonState.postValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
  }

  void onImageEditorStarted() {
    countButtonVisibility = CountButtonState.Visibility.FORCED_OFF;
    countButtonState.postValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
  }

  void onImageEditorEnded() {
    countButtonVisibility = CountButtonState.Visibility.CONDITIONAL;
    countButtonState.postValue(new CountButtonState(getSelectedMediaOrDefault().size(), countButtonVisibility));
  }

  void onBodyChanged(@NonNull CharSequence body) {
    this.body = body;
  }

  void onFolderSelected(@NonNull String bucketId) {
    this.bucketId.setValue(bucketId);
    bucketMedia.setValue(Collections.emptyList());
  }

  void onPageChanged(int position) {
    this.position.setValue(position);
  }

  void onMediaItemRemoved(int position) {
    getSelectedMediaOrDefault().remove(position);
    selectedMedia.setValue(selectedMedia.getValue());
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

  CharSequence getBody() {
    return body;
  }

  LiveData<Integer> getPosition() {
    return position;
  }

  LiveData<String> getBucketId() {
    return bucketId;
  }

  LiveData<Error> getError() {
    return error;
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

  enum Error {
    ITEM_TOO_LARGE
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

    boolean getVisibility() {
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

    private final MediaRepository repository;

    Factory(@NonNull MediaRepository repository) {
      this.repository = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new MediaSendViewModel(repository));
    }
  }
}
