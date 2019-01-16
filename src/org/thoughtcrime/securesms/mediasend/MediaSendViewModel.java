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

import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

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
  private final MutableLiveData<Optional<String>>  bucketId;
  private final MutableLiveData<List<MediaFolder>> folders;
  private final Map<Uri, Object>                   savedDrawState;

  private MediaSendViewModel(@NonNull MediaRepository repository) {
    this.repository     = repository;
    this.selectedMedia  = new MutableLiveData<>();
    this.bucketMedia    = new MutableLiveData<>();
    this.position       = new MutableLiveData<>();
    this.bucketId       = new MutableLiveData<>();
    this.folders        = new MutableLiveData<>();
    this.savedDrawState = new HashMap<>();

    position.setValue(-1);
  }

  void setInitialSelectedMedia(@NonNull List<Media> newMedia) {
    List<Media> filteredMedia       = getFilteredMedia(newMedia);
    boolean     allBucketsPopulated = Stream.of(filteredMedia).reduce(true, (populated, m) -> populated && m.getBucketId().isPresent());

    selectedMedia.setValue(filteredMedia);
    bucketId.setValue(allBucketsPopulated ? computeBucketId(filteredMedia) : Optional.absent());
  }

  void onSelectedMediaChanged(@NonNull List<Media> newMedia) {
    List<Media> filteredMedia = getFilteredMedia(newMedia);

    selectedMedia.setValue(filteredMedia);
    position.setValue(filteredMedia.isEmpty() ? -1 : 0);
  }

  void onFolderSelected(@NonNull String bucketId) {
    this.bucketId.setValue(Optional.of(bucketId));
    bucketMedia.setValue(Collections.emptyList());
  }

  void onPageChanged(int position) {
    this.position.setValue(position);
  }

  void onMediaItemRemoved(int position) {
    selectedMedia.getValue().remove(position);
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

  LiveData<List<Media>> getSelectedMedia() {
    return selectedMedia;
  }

  LiveData<List<Media>> getMediaInBucket(@NonNull Context context, @NonNull String bucketId) {
    repository.getMediaInBucket(context, bucketId, bucketMedia::postValue);
    return bucketMedia;
  }

  @NonNull LiveData<List<MediaFolder>> getFolders(@NonNull Context context) {
    repository.getFolders(context, folders::postValue);
    return folders;
  }

  LiveData<Integer> getPosition() {
    return position;
  }

  LiveData<Optional<String>> getBucketId() {
    return bucketId;
  }

  private Optional<String> computeBucketId(@NonNull List<Media> media) {
    if (media.isEmpty() || !media.get(0).getBucketId().isPresent()) return Optional.absent();

    String candidate = media.get(0).getBucketId().get();
    for (int i = 1; i < media.size(); i++) {
      if (!Util.equals(candidate, media.get(i).getBucketId().orNull())) {
        return Optional.of(Media.ALL_MEDIA_BUCKET_ID);
      }
    }

    return Optional.of(candidate);
  }

  private @NonNull List<Media> getFilteredMedia(@NonNull List<Media> media) {
    return Stream.of(media).filter(m -> MediaUtil.isGif(m.getMimeType())       ||
                                        MediaUtil.isImageType(m.getMimeType()) ||
                                        MediaUtil.isVideoType(m.getMimeType())).toList();

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
