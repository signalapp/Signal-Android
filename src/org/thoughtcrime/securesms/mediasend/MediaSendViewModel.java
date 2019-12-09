package org.thoughtcrime.securesms.mediasend;

import android.app.Application;

import androidx.annotation.Nullable;
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
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
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
  private final MutableLiveData<Optional<Media>>   mostRecentMedia;
  private final MutableLiveData<Integer>           position;
  private final MutableLiveData<String>            bucketId;
  private final MutableLiveData<List<MediaFolder>> folders;
  private final MutableLiveData<HudState>          hudState;
  private final SingleLiveEvent<Error>             error;
  private final Map<Uri, Object>                   savedDrawState;

  private MediaConstraints mediaConstraints;
  private CharSequence     body;
  private boolean          sentMedia;
  private int              maxSelection;
  private Page             page;
  private boolean          isSms;
  private Optional<Media>  lastCameraCapture;

  private boolean       hudVisible;
  private boolean       composeVisible;
  private boolean       captionVisible;
  private ButtonState   buttonState;
  private RailState     railState;
  private ViewOnceState viewOnceState;

  private @Nullable Recipient recipient;

  private MediaSendViewModel(@NonNull Application application, @NonNull MediaRepository repository) {
    this.application       = application;
    this.repository        = repository;
    this.selectedMedia     = new MutableLiveData<>();
    this.bucketMedia       = new MutableLiveData<>();
    this.mostRecentMedia   = new MutableLiveData<>();
    this.position          = new MutableLiveData<>();
    this.bucketId          = new MutableLiveData<>();
    this.folders           = new MutableLiveData<>();
    this.hudState          = new MutableLiveData<>();
    this.error             = new SingleLiveEvent<>();
    this.savedDrawState    = new HashMap<>();
    this.lastCameraCapture = Optional.absent();
    this.body              = "";
    this.buttonState       = ButtonState.GONE;
    this.railState         = RailState.GONE;
    this.viewOnceState     = ViewOnceState.GONE;
    this.page              = Page.UNKNOWN;

    position.setValue(-1);
  }

  void setTransport(@NonNull TransportOption transport) {
    if (transport.isSms()) {
      isSms            = true;
      maxSelection     = MAX_SMS;
      mediaConstraints = MediaConstraints.getMmsMediaConstraints(transport.getSimSubscriptionId().or(-1));
    } else {
      isSms            = false;
      maxSelection     = MAX_PUSH;
      mediaConstraints = MediaConstraints.getPushMediaConstraints();
    }
  }

  void setRecipient(@Nullable Recipient recipient) {
    this.recipient = recipient;
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
        }

        if (page == Page.EDITOR && filteredMedia.isEmpty()) {
          error.postValue(Error.NO_ITEMS);
        } else if (filteredMedia.isEmpty()) {
          hudVisible = false;
          selectedMedia.setValue(filteredMedia);
          hudState.setValue(buildHudState());
        } else {
          hudVisible = true;
          selectedMedia.setValue(filteredMedia);
          hudState.setValue(buildHudState());
        }
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

        selectedMedia.setValue(filteredMedia);
      });
    });
  }

  void onMultiSelectStarted() {
    hudVisible     = true;
    composeVisible = false;
    captionVisible = false;
    buttonState    = ButtonState.COUNT;
    railState      = RailState.VIEWABLE;
    viewOnceState  = ViewOnceState.GONE;

    hudState.setValue(buildHudState());
  }

  void onImageEditorStarted() {
    page           = Page.EDITOR;
    hudVisible     = true;
    composeVisible = viewOnceState != ViewOnceState.ENABLED;
    captionVisible = getSelectedMediaOrDefault().size() > 1 || (getSelectedMediaOrDefault().size() > 0 && getSelectedMediaOrDefault().get(0).getCaption().isPresent());
    buttonState    = (recipient != null) ? ButtonState.SEND : ButtonState.CONTINUE;

    if (viewOnceState == ViewOnceState.GONE && viewOnceSupported()) {
      if (FeatureFlags.VIEW_ONCE_SENDING) {
        viewOnceState = TextSecurePreferences.isRevealableMessageEnabled(application) ? ViewOnceState.ENABLED : ViewOnceState.DISABLED;
      } else {
        viewOnceState = ViewOnceState.GONE;
      }
    } else if (!viewOnceSupported()) {
      viewOnceState = ViewOnceState.GONE;
    }

    railState  = !isSms && viewOnceState != ViewOnceState.ENABLED ? RailState.INTERACTIVE : RailState.GONE;

    hudState.setValue(buildHudState());
  }

  void onCameraStarted() {
    // TODO: Don't need this?
    Page previous = page;

    page          = Page.CAMERA;
    hudVisible    = false;
    viewOnceState = ViewOnceState.GONE;
    buttonState   = ButtonState.COUNT;

    List<Media> selected = getSelectedMediaOrDefault();

    if (previous == Page.EDITOR && lastCameraCapture.isPresent() && selected.contains(lastCameraCapture.get()) && selected.size() == 1) {
      selected.remove(lastCameraCapture.get());
      selectedMedia.setValue(selected);
      BlobProvider.getInstance().delete(application, lastCameraCapture.get().getUri());
    }

    hudState.setValue(buildHudState());
  }

  void onItemPickerStarted() {
    page           = Page.ITEM_PICKER;
    hudVisible     = true;
    composeVisible = false;
    captionVisible = false;
    buttonState    = ButtonState.COUNT;
    viewOnceState  = ViewOnceState.GONE;
    railState      = getSelectedMediaOrDefault().isEmpty() ? RailState.GONE : RailState.VIEWABLE;

    lastCameraCapture = Optional.absent();

    hudState.setValue(buildHudState());
  }

  void onFolderPickerStarted() {
    page           = Page.FOLDER_PICKER;
    hudVisible     = true;
    composeVisible = false;
    captionVisible = false;
    buttonState    = ButtonState.COUNT;
    viewOnceState  = ViewOnceState.GONE;
    railState      = getSelectedMediaOrDefault().isEmpty() ? RailState.GONE : RailState.VIEWABLE;

    lastCameraCapture = Optional.absent();

    hudState.setValue(buildHudState());
  }

  void onContactSelectStarted() {
    hudVisible = false;

    hudState.setValue(buildHudState());
  }

  void onRevealButtonToggled() {
    hudVisible     = true;
    viewOnceState  = viewOnceState == ViewOnceState.ENABLED ? ViewOnceState.DISABLED : ViewOnceState.ENABLED;
    composeVisible = viewOnceState != ViewOnceState.ENABLED;
    railState      = viewOnceState == ViewOnceState.ENABLED || isSms ? RailState.GONE : RailState.INTERACTIVE;
    captionVisible = false;

    List<Media> uncaptioned = Stream.of(getSelectedMediaOrDefault())
                                    .map(m -> new Media(m.getUri(), m.getMimeType(), m.getDate(), m.getWidth(), m.getHeight(), m.getSize(), m.getBucketId(), Optional.absent()))
                                    .toList();

    selectedMedia.setValue(uncaptioned);

    TextSecurePreferences.setIsRevealableMessageEnabled(application, viewOnceState == ViewOnceState.ENABLED);

    hudState.setValue(buildHudState());
  }

  void onKeyboardHidden(boolean isSms) {
    if (page != Page.EDITOR) return;

    composeVisible = (viewOnceState != ViewOnceState.ENABLED);
    buttonState    = (recipient != null) ? ButtonState.SEND : ButtonState.CONTINUE;

    if (isSms) {
      railState      = RailState.GONE;
      captionVisible = false;
    } else {
      railState = viewOnceState != ViewOnceState.ENABLED ? RailState.INTERACTIVE : RailState.GONE;

      if (getSelectedMediaOrDefault().size() > 1 || (getSelectedMediaOrDefault().size() > 0 && getSelectedMediaOrDefault().get(0).getCaption().isPresent())) {
        captionVisible = true;
      }
    }

    hudState.setValue(buildHudState());
  }

  void onKeyboardShown(boolean isComposeFocused, boolean isCaptionFocused, boolean isSms) {
    if (page != Page.EDITOR) return;

    if (isSms) {
      railState      = RailState.GONE;
      composeVisible = (viewOnceState == ViewOnceState.GONE);
      captionVisible = false;
      buttonState    = (recipient != null) ? ButtonState.SEND : ButtonState.CONTINUE;
    } else {
      if (isCaptionFocused) {
        railState      = viewOnceState != ViewOnceState.ENABLED ? RailState.INTERACTIVE : RailState.GONE;
        composeVisible = false;
        captionVisible = true;
        buttonState    = ButtonState.GONE;
      } else if (isComposeFocused) {
        railState      = viewOnceState != ViewOnceState.ENABLED ? RailState.INTERACTIVE : RailState.GONE;
        composeVisible = (viewOnceState != ViewOnceState.ENABLED);
        captionVisible = false;
        buttonState    = (recipient != null) ? ButtonState.SEND : ButtonState.CONTINUE;
      }
    }

    hudState.setValue(buildHudState());
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

    if (page == Page.EDITOR && getSelectedMediaOrDefault().isEmpty()) {
      error.setValue(Error.NO_ITEMS);
    } else {
      selectedMedia.setValue(selectedMedia.getValue());
    }

    if (getSelectedMediaOrDefault().size() > 0) {
      this.position.setValue(Math.min(position, getSelectedMediaOrDefault().size() - 1));
    }

    if (getSelectedMediaOrDefault().size() == 1) {
      viewOnceState = viewOnceSupported() ? ViewOnceState.DISABLED : ViewOnceState.GONE;
    }

    hudState.setValue(buildHudState());
  }

  void onMediaCaptured(@NonNull Media media) {
    lastCameraCapture = Optional.of(media);

    List<Media> selected = selectedMedia.getValue();

    if (selected == null) {
      selected = new LinkedList<>();
    }

    if (selected.size() >= maxSelection) {
      error.setValue(Error.TOO_MANY_ITEMS);
      return;
    }

    selected.add(media);
    selectedMedia.setValue(selected);
    position.setValue(selected.size() - 1);
    bucketId.setValue(Media.ALL_MEDIA_BUCKET_ID);
  }

  void onCaptionChanged(@NonNull String newCaption) {
    if (position.getValue() >= 0 && !Util.isEmpty(selectedMedia.getValue())) {
      selectedMedia.getValue().get(position.getValue()).setCaption(TextUtils.isEmpty(newCaption) ? null : newCaption);
    }
  }

  void onCameraControlsInitialized() {
    repository.getMostRecentItem(application, mostRecentMedia::postValue);
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

  @NonNull LiveData<Optional<Media>> getMostRecentMediaItem(@NonNull Context context) {
    return mostRecentMedia;
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

  @NonNull LiveData<HudState> getHudState() {
    return hudState;
  }

  int getMaxSelection() {
    return maxSelection;
  }

  boolean isViewOnce() {
    return FeatureFlags.VIEW_ONCE_SENDING && viewOnceState == ViewOnceState.ENABLED;
  }

  @NonNull MediaConstraints getMediaConstraints() {
    return mediaConstraints;
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
                                    (MediaUtil.isVideoType(m.getMimeType()) && m.getSize() < mediaConstraints.getUncompressedVideoMaxSize(context));
                           }).toList();

  }

  private HudState buildHudState() {
    ViewOnceState updatedViewOnceState  = FeatureFlags.VIEW_ONCE_SENDING ? viewOnceState : ViewOnceState.GONE;
    List<Media>   selectedMedia         = getSelectedMediaOrDefault();
    int           selectionCount        = selectedMedia.size();
    ButtonState   updatedButtonState    = buttonState == ButtonState.COUNT && selectionCount == 0 ? ButtonState.GONE : buttonState;
    boolean       updatedCaptionVisible = captionVisible && (selectedMedia.size() > 1 || (selectedMedia.size() > 0 && selectedMedia.get(0).getCaption().isPresent()));

    return new HudState(hudVisible, composeVisible, updatedCaptionVisible, selectionCount, updatedButtonState, railState, updatedViewOnceState);
  }

  private void clearPersistedMedia() {
    Stream.of(getSelectedMediaOrDefault())
          .map(Media::getUri)
          .filter(BlobProvider::isAuthority)
          .forEach(uri -> BlobProvider.getInstance().delete(application.getApplicationContext(), uri));
  }

  private boolean viewOnceSupported() {
    return !isSms && (recipient == null || !recipient.isLocalNumber()) && mediaSupportsRevealableMessage(getSelectedMediaOrDefault());
  }

  private boolean mediaSupportsRevealableMessage(@NonNull List<Media> media) {
    if (media.size() != 1) return false;
    return MediaUtil.isImageOrVideoType(media.get(0).getMimeType());
  }

  @Override
  protected void onCleared() {
    if (!sentMedia) {
      clearPersistedMedia();
    }
  }

  enum Error {
    ITEM_TOO_LARGE, TOO_MANY_ITEMS, NO_ITEMS
  }

  enum Page {
    CAMERA, ITEM_PICKER, FOLDER_PICKER, EDITOR, CONTACT_SELECT, UNKNOWN
  }

  enum ButtonState {
    COUNT, SEND, CONTINUE, GONE
  }

  enum RailState {
    INTERACTIVE, VIEWABLE, GONE
  }

  enum ViewOnceState {
    ENABLED, DISABLED, GONE
  }

  static class HudState {

    private final boolean     hudVisible;
    private final boolean     composeVisible;
    private final boolean     captionVisible;
    private final int         selectionCount;
    private final ButtonState buttonState;
    private final RailState   railState;
    private final ViewOnceState viewOnceState;

    HudState(boolean hudVisible,
             boolean composeVisible,
             boolean captionVisible,
             int selectionCount,
             @NonNull ButtonState buttonState,
             @NonNull RailState railState,
             @NonNull ViewOnceState viewOnceState)
    {
      this.hudVisible      = hudVisible;
      this.composeVisible  = composeVisible;
      this.captionVisible  = captionVisible;
      this.selectionCount  = selectionCount;
      this.buttonState     = buttonState;
      this.railState       = railState;
      this.viewOnceState = viewOnceState;
    }

    public boolean isHudVisible() {
      return hudVisible;
    }

    public boolean isComposeVisible() {
      return hudVisible && composeVisible;
    }

    public boolean isCaptionVisible() {
      return hudVisible && captionVisible;
    }

    public int getSelectionCount() {
      return selectionCount;
    }

    public @NonNull ButtonState getButtonState() {
      return buttonState;
    }

    public @NonNull RailState getRailState() {
      return hudVisible ? railState : RailState.GONE;
    }

    public @NonNull ViewOnceState getViewOnceState() {
      return hudVisible ? viewOnceState : ViewOnceState.GONE;
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
